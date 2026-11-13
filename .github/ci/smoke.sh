#!/usr/bin/env bash
# Emulator smoke test: install, launch, capture key screens of the glass UI.
# Usage: smoke.sh <apk> <outdir>
set -uo pipefail
APK="$1"; OUT="$2"
# applicationId is NOT the code namespace: the app installs as
# com.destiny.app while its classes stay in com.openminis.app, so
# `am start -n <PKG>/com.openminis.app.MainActivity` needs the
# applicationId on the LEFT of the slash.
#
# This used to be hardcoded to a stale value (com.dl1991.mibot). After the
# rename, every `am start` below failed with "Activity class ... does not
# exist", zero screenshots were usable and the job was reported as an app
# crash — the app itself never crashed. So: read the value off the APK.
AAPT2="$(command -v aapt2 2>/dev/null || true)"
if [ -z "$AAPT2" ]; then
  for d in "$ANDROID_HOME"/build-tools/*/aapt2 "$ANDROID_SDK_ROOT"/build-tools/*/aapt2; do
    [ -x "$d" ] && AAPT2="$d" && break
  done
fi
PKG=""
if [ -n "$AAPT2" ]; then
  PKG="$("$AAPT2" dump packagename "$APK" 2>/dev/null | tr -d '\r' | head -n1)"
fi
if [ -z "$PKG" ]; then
  PKG="$(aapt dump badging "$APK" 2>/dev/null | tr -d '\r' \
        | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n1)"
fi
if [ -z "${PKG:-}" ]; then
  PKG=com.destiny.app
  echo "::warning::package name not readable from APK, assuming $PKG"
fi
echo "smoke: APK=$APK PKG=$PKG OUT=$OUT aapt2=${AAPT2:-none}"
mkdir -p "$OUT"
HERE="$(cd "$(dirname "$0")" && pwd)"
fail=0

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot: $1 ($(stat -c%s "$OUT/$1.png") bytes)"; }
alive() { adb shell pidof $PKG >/dev/null 2>&1; }
tap() { python3 "$HERE/tap.py" "$@"; }
link() { adb shell am start -W -a android.intent.action.VIEW -d "minis://settings/$1" $PKG >/dev/null; }
launch() { adb shell am start -W -n $PKG/com.openminis.app.MainActivity --ez glass_demo true > "$OUT/start-$1.txt"; }

adb shell getprop ro.product.cpu.abilist | tee "$OUT/abilist.txt"
adb logcat -c || true
adb install -r -g "$APK" 2>&1 | tee "$OUT/install.txt"
grep -q Success "$OUT/install.txt" || { echo "::error::install failed"; exit 1; }

# Guard: with a wrong applicationId every `am start` below fails with
# "Activity class ... does not exist", and the run then looks exactly like
# an app crash. Fail loudly and early instead.
PKG_OK=0
for i in 1 2 3 4 5; do
  if adb shell pm list packages 2>/dev/null | tr -d '\r' | grep -qx "package:$PKG"; then
    PKG_OK=1; break
  fi
  # The emulator's adbd can reconnect right after boot; a single empty
  # `pm list packages` is not proof the install failed.
  echo "smoke: package not visible yet, retry $i/5"; sleep 2
done
if [ "$PKG_OK" -ne 1 ]; then
  echo "::error::package $PKG is not installed after a successful install — PKG is wrong"
  exit 1
fi
echo "smoke: $PKG installed"

# [destiny/v7] The launcher is the NATIVE Compose app again. Earlier builds
# pointed all three aliases at DestinyWebActivity (an HTML design mock in a
# WebView), so `launch()` starting MainActivity directly hid the fact that
# tapping the icon opened a web page. Resolve the LAUNCHER intent and make
# sure it is NOT the WebView shell, then start MainActivity explicitly —
# an implicit MAIN/LAUNCHER am start resolves to the HOME screen on these
# emulator images and produces a false negative.
RESOLVED="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $PKG 2>/dev/null | tr -d '\r' | tail -n1)"
echo "smoke: launcher resolves to $RESOLVED" | tee "$OUT/launcher-resolve.txt"
if [ -z "$RESOLVED" ]; then
  echo "::error::no LAUNCHER activity resolved for $PKG"
  fail=1
elif echo "$RESOLVED" | grep -q "DestinyWebActivity"; then
  echo "::error::launcher still resolves to the WebView shell"
  fail=1
fi
adb shell am force-stop $PKG; sleep 1
adb shell am start -W -n $PKG/com.openminis.app.MainActivity > "$OUT/start-native.txt" 2>&1 || true
sleep 8
shot 00-native-launcher 0
adb shell dumpsys activity activities > "$OUT/activities.txt" 2>/dev/null || true
if grep -m1 "mResumedActivity" "$OUT/activities.txt" | grep -q "MainActivity"; then
  echo "smoke: native MainActivity is resumed"
else
  echo "::warning::could not confirm resumed MainActivity from dumpsys (screenshot is the evidence)"
fi
adb shell am force-stop $PKG; sleep 1

adb shell settings put system screen_off_timeout 1800000 || true
adb shell svc power stayon true || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true

# glass_demo (debug builds only) fills the sidebar's recent list with sample
# chats/groups so a fresh emulator has something to render.
launch en
sleep 25
alive || { echo "::error::app not running after launch"; fail=1; }
shot 01-chats 2

# [destiny/v7-full] The start screen is the Chinese V7 design home now, so
# the old English sidebar/tab taps are informational only — a miss must NOT
# fail the build. The V7 deep-link shots below are the real visual evidence.
# [destiny/v7-deeplink] Only press BACK when the sidebar actually opened.
# On the V7 home the old "Open sidebar" desc doesn't exist — a blind BACK
# here FINISHES MainActivity at the root, every later tap runs against a
# dead app, and each `link` below silently becomes a cold start. (Cold
# start deep links now mount correctly via startDestination, but warm
# start is the realistic path — keep the app alive.)
if tap desc "Open sidebar"; then
  shot 02-sidebar 3
  adb shell input keyevent KEYCODE_BACK; sleep 2
else
  echo "::warning::legacy sidebar button not found (app stays on V7 home)"
fi

# [destiny/v7] Four tabs now: Chats / Feed / Group chats / Settings.
# Ideas is NOT a tab any more — it is the second state inside Feed.
# Keep the old assertion and the job would fail trying to tap a tab we removed.
if tap desc "Feed"; then
  shot 03-feed 3
  # Prove the tri-state is real, not a picture: tap both extra modes and shoot.
  if tap text "想到的"; then shot 03b-feed-ideas 3; else echo "::warning::feed mode 想到的 not found"; fi
  if tap text "能领的"; then shot 03c-feed-quotas 3; else echo "::warning::feed mode 能领的 not found"; fi
  tap text "在看" >/dev/null 2>&1 || true
else
  echo "::warning::legacy tab Feed not found (V7 home uses Chinese tabs)"
fi
for t in "Group chats":05-groups Settings:06-settings; do
  name="${t%%:*}"; file="${t##*:}"
  if tap desc "$name"; then shot "$file" 3; else echo "::warning::legacy tab $name not found"; fi
done
# [destiny/v7] 服务器 sits in Settings › Network right below Tailscale.
# Scroll a little if the row is below the fold; a miss is a warning, not a
# verdict on the app.
SERVERS_OK=0
for i in 1 2 3; do
  if tap desc "Servers" || tap text "Servers" || tap text "服务器"; then SERVERS_OK=1; break; fi
  adb shell input swipe 540 1600 540 700 400; sleep 1
done
if [ "$SERVERS_OK" -eq 1 ]; then
  shot 06b-servers 3
  adb shell input keyevent KEYCODE_BACK; sleep 2
else
  echo "::warning::Servers row not found in Settings"
fi
# Settings page scrolled to the Appearance / Feedback rows.
adb shell input swipe 540 1700 540 900 400; shot 07-settings-scrolled 2

# [destiny/v7] Deep-link straight into the new native pages so CI has real
# pixel evidence for voice, the 56-screen index, a single spec screen and the
# server vault. These are Compose routes, not HTML.
link voice;          shot 09-voice 4
link all-screens;    shot 10-all-screens 4
link spec-screen/34; shot 11-spec-34 4
link servers;        shot 12-servers 4
# [destiny/v7-full] The design spec IS the app UI now. Shoot the V7 home plus
# the four screens the user called out, so UI parity is checked in real pixels.
link home;           shot 13-v7-home 4
link v7-screen/01;   shot 14-v7-sessions 4
link v7-screen/16;   shot 15-v7-settings 4
link v7-screen/46;   shot 16-v7-capabilities 4
link v7-screen/45c;  shot 17-v7-server-detail 4
link v7-screen/19;   shot 18-v7-mika 4
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true

adb shell am start -W -a android.intent.action.VIEW -d "minis://settings/backdrop" $PKG >/dev/null
shot 08-backdrop-settings 4

# Style B (warm white): switch, look at Chats + sidebar, then switch back to A.
if tap text "Warm white"; then
  shot 08b-backdrop-warm 2
  adb shell input keyevent KEYCODE_BACK; sleep 2
  tap desc "Chats" && shot 12-chats-warm 3
  tap desc "Open sidebar" && shot 13-sidebar-warm 3
  adb shell input keyevent KEYCODE_BACK; sleep 2
  adb shell am start -W -a android.intent.action.VIEW -d "minis://settings/backdrop" $PKG >/dev/null; sleep 3
  tap text "Dusk glass" || true
  adb shell input keyevent KEYCODE_BACK; sleep 2
fi
tap desc "Chats" >/dev/null 2>&1 || true

# Mibot v2 Chinese pass, light + dark. ci_lang/ci_theme/ci_vault_demo are
# debug-only launch extras (MainActivity); language applies from the next
# process start on API < 33, so seed once, then relaunch per theme.
ZH="$OUT/zh"; mkdir -p "$ZH"
zlaunch() { adb shell am force-stop $PKG; sleep 1
  adb shell am start -W -n $PKG/com.openminis.app.MainActivity --ez glass_demo true \
    --es ci_lang zh-CN --ez ci_vault_demo true --ei ci_theme "$1" > "$ZH/start-$2.txt"; sleep "${3:-12}"; }
zshot() { sleep "${2:-3}"; adb exec-out screencap -p > "$ZH/$1.png"; echo "zh shot: $1 ($(stat -c%s "$ZH/$1.png") bytes)"; }
link() { adb shell am start -W -a android.intent.action.VIEW -d "minis://settings/$1" $PKG >/dev/null; }
# Back to the chats tab; relaunch if Back left the app.
front() { adb shell dumpsys window 2>/dev/null | grep -E "mCurrentFocus|mFocusedApp" | grep -q "$PKG"; }
home() { adb shell input keyevent KEYCODE_BACK; sleep 2
  front || { adb shell am start -W -n $PKG/com.openminis.app.MainActivity >/dev/null; sleep 4; }
  tap desc "对话" >/dev/null 2>&1 || true; sleep 1; }
adb shell am force-stop $PKG; adb shell pm clear $PKG >/dev/null 2>&1 || true
zlaunch 1 seed 15
for th in 1:light 2:dark; do
  m="${th%%:*}"; n="${th##*:}"
  zlaunch "$m" "$n" 14
  alive || { echo "::error::app not running (zh $n)"; fail=1; }
  zshot "$n-01-onboarding" 1
  tap desc "群聊" && zshot "$n-04-groups" 3 || echo "::warning::zh 群聊 tab not found ($n)"
  tap desc "设置" && zshot "$n-05-settings" 3 || echo "::warning::zh 设置 tab not found ($n)"
  tap desc "对话" >/dev/null 2>&1 || true; sleep 2
  if tap desc "打开侧边栏"; then
    zshot "$n-03-sidebar" 3
    if tap desc "新对话"; then zshot "$n-02-chat" 3; adb shell input keyevent KEYCODE_BACK; sleep 1; else echo "::warning::zh new-chat not found ($n)"; fi
  else echo "::warning::zh sidebar button not found ($n)"; fi
  home
  link vault;        zshot "$n-06-vault" 4;    home
  link gateway;      zshot "$n-07-gateway" 4;  home
  link sandbox;      zshot "$n-08-sandbox" 4;  home
  link add-provider; zshot "$n-09-add-provider" 4; home
  link virtual-display; zshot "$n-10-vd" 4
  adb shell input swipe 540 1700 540 700 400; zshot "$n-11-vd-scrolled" 2; home
  link backdrop;     zshot "$n-12-backdrop" 4; home
  # [fork/mibot-v5] 插件市场: list (both sections), a category with desktop
  # plugins, and one plugin's detail page (needs network on the emulator).
  link plugins;      zshot "$n-13-plugins" 10
  adb shell input swipe 540 1700 540 500 500; zshot "$n-14-plugins-scrolled" 3
  home
  adb shell "am start -W -a android.intent.action.VIEW -d 'minis://settings/plugins?section=desktop' $PKG" >/dev/null
  zshot "$n-15-plugins-desktop" 10; home
  adb shell "am start -W -a android.intent.action.VIEW -d 'minis://settings/plugins?tab=efficiency' $PKG" >/dev/null
  zshot "$n-16-plugins-efficiency" 10
  adb shell input swipe 540 1700 540 500 500; zshot "$n-16b-plugins-efficiency-scrolled" 3; home
  adb shell "am start -W -a android.intent.action.VIEW -d 'minis://settings/plugin?name=dsh-market/dsh-market' $PKG" >/dev/null
  zshot "$n-17-plugin-detail" 12
  adb shell input swipe 540 1700 540 600 500; zshot "$n-18-plugin-detail-scrolled" 3; home
  # [fork/mibot-v5b2] 邮箱台账 (demo loopback boxes), AI 邮箱 tab, 权限与自动化,
  # dsh-our-free-model detail (adapter audit + MIT attribution), 连接方式 sheets.
  link mail;         zshot "$n-19-mail-ledger" 8
  adb shell input swipe 540 1700 540 600 500; zshot "$n-19b-mail-ledger-scrolled" 3; home
  link ai-mail;      zshot "$n-20-ai-mail" 6; home
  link capabilities; zshot "$n-21-capabilities" 5
  adb shell input swipe 540 1700 540 600 500; zshot "$n-21b-capabilities-scrolled" 3; home
  adb shell "am start -W -a android.intent.action.VIEW -d 'minis://settings/plugin?name=ebony-vinyl/dsh-our-free-model' $PKG" >/dev/null
  zshot "$n-22-plugin-ofm" 12
  adb shell input swipe 540 1700 540 600 500; zshot "$n-22b-plugin-ofm-audit" 3
  adb shell input swipe 540 1700 540 600 500; zshot "$n-22c-plugin-ofm-more" 3; home
  # [fork/mibot-v5b4] DSH host verdicts: skill-only (手机能用), blocked (不支持), missing services (待支持).
  adb shell "am start -W -a android.intent.action.VIEW -d 'minis://settings/plugin?name=alchaincyf/nuwa-skill' $PKG" >/dev/null
  zshot "$n-25-plugin-nuwa" 12
  adb shell input swipe 540 1700 540 600 500; zshot "$n-25b-plugin-nuwa-host" 3; home
  adb shell "am start -W -a android.intent.action.VIEW -d 'minis://settings/plugin?name=yujunzhixue/dsh-purge' $PKG" >/dev/null
  zshot "$n-26-plugin-purge" 12; home
  adb shell "am start -W -a android.intent.action.VIEW -d 'minis://settings/plugin?name=liustack/modlens' $PKG" >/dev/null
  zshot "$n-27-plugin-modlens" 12; home
  adb shell "am start -W -a android.intent.action.VIEW -d 'minis://settings/vault-discover?connect=hunyuan' $PKG" >/dev/null
  zshot "$n-23-connect-hunyuan" 6; home
  adb shell "am start -W -a android.intent.action.VIEW -d 'minis://settings/vault-discover?connect=github' $PKG" >/dev/null
  zshot "$n-24-connect-github" 6; home
  alive || { echo "::error::app died during zh $n pass"; fail=1; }
done
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb exec-out cat /sdcard/ui.xml > "$ZH/last-ui.xml" || true

# [fork/mibot-v5b4] DSH host on the emulator (best effort, never fails the run):
# install nuwa-skill, press 启动 → Node 22 in the sandbox → skills imported.
# The x86_64 image runs the arm64 app through native-bridge translation, so the
# PRoot sandbox may not come up here; the plugin page then shows 失败 + its log.
DSH="$OUT/dsh"; mkdir -p "$DSH"
adb shell "am start -W -a android.intent.action.VIEW -d 'minis://settings/plugin?name=alchaincyf/nuwa-skill' $PKG" >/dev/null; sleep 12
if tap text "试装"; then sleep 40; fi
adb exec-out screencap -p > "$DSH/1-installed.png"
if tap text "启动" || { adb shell input swipe 540 1700 540 1100 500; sleep 2; tap text "启动"; }; then
  for i in $(seq 1 24); do sleep 10; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb exec-out cat /sdcard/ui.xml > "$DSH/ui.xml"; grep -qE 'text="(运行中|失败|待支持)"' "$DSH/ui.xml" && break; done
  adb exec-out screencap -p > "$DSH/2-started.png"
  grep -oE 'text="(运行中|失败|待支持|准备中|未启动)"' "$DSH/ui.xml" | head -3 | tee "$DSH/state.txt"
  tap text "查看日志（$(grep -oE '查看日志（[0-9]+）' "$DSH/ui.xml" | grep -oE '[0-9]+' | head -1)）" >/dev/null 2>&1 || true
  sleep 2; adb exec-out screencap -p > "$DSH/3-log.png"
  adb shell input swipe 540 1700 540 700 500; sleep 2; adb exec-out screencap -p > "$DSH/4-log-scrolled.png"
else echo "::warning::DSH 启动 button not found"; fi
adb logcat -d -v time | grep -E "DshHost|MibotPlugins" > "$DSH/log.txt" || true
alive || { echo "::error::app died during DSH host attempt"; fail=1; }
home

# [fork/mibot-v3] Hidden-display end-to-end (Android 12+): the app writes its
# "start from a computer" script, adb (shell uid) runs it -> helper hands its
# Binder to the app. Then the app is force-stopped and relaunched: the helper
# must re-attach on its own. The VD page (ci_vd_demo) creates a hidden display
# and starts Settings on it; the main screen must keep showing Mibot.
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [ "${SDK:-0}" -ge 31 ]; then
  VD="$OUT/vd"; mkdir -p "$VD"
  adb shell am force-stop $PKG; sleep 1
  adb shell am start -W -n $PKG/com.openminis.app.MainActivity --ez ci_vd_usb true > "$VD/start-usb.txt"; sleep 8
  SCRIPT=/sdcard/Android/data/$PKG/files/usb-start.sh
  adb shell ls -l "$SCRIPT" > "$VD/script-ls.txt" 2>&1 || true
  adb shell sh "$SCRIPT" > "$VD/usb-start-out.txt" 2>&1 || true
  cat "$VD/usb-start-out.txt"
  sleep 5
  adb shell "ps -A -o USER,PID,NAME | grep vdhelper" > "$VD/helper-ps.txt" 2>&1 || true
  cat "$VD/helper-ps.txt"
  adb shell am force-stop $PKG; sleep 2
  # Relaunch (the helper must re-attach by itself), then open the page via deep link.
  adb shell am start -W -n $PKG/com.openminis.app.MainActivity --ez ci_vd_demo true > "$VD/start-demo.txt"; sleep 12
  link virtual-display; sleep 30
  adb exec-out screencap -p > "$VD/vd-live.png"; echo "vd shot: $(stat -c%s "$VD/vd-live.png") bytes"
  adb shell dumpsys display > "$VD/dumpsys-display.txt" 2>&1 || true
  grep -n "mibot-agent" "$VD/dumpsys-display.txt" | head -5 || echo "no mibot-agent display"
  adb shell dumpsys activity activities > "$VD/activities.txt" 2>&1 || true
  grep -nE "Display #|com.android.settings" "$VD/activities.txt" | head -20 || true
  adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" > "$VD/focus.txt" 2>&1 || true
  cat "$VD/focus.txt"
  adb shell input swipe 540 1600 540 600 400; sleep 2
  adb exec-out screencap -p > "$VD/vd-live-log.png"
  adb logcat -d -v time | grep -E "MibotHelper|MibotShell|MibotHelperHub|MibotPhone" > "$VD/helper-log.txt" || true
  tail -40 "$VD/helper-log.txt"
  alive || { echo "::error::app died during vd e2e"; fail=1; }
fi

alive || { echo "::error::app died during smoke test"; fail=1; }
adb logcat -d -v time > "$OUT/logcat.txt" || true
grep -E "FATAL EXCEPTION" -A30 "$OUT/logcat.txt" | head -120 > "$OUT/crash.txt" || true
if [ -s "$OUT/crash.txt" ]; then echo "::error::crash in logcat"; cat "$OUT/crash.txt"; fail=1; fi
exit $fail
