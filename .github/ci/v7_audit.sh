#!/usr/bin/env bash
# [destiny/v7-audit] 逐项体检：5 个 tab + 设置里 15 个入口逐个点开，截图 + 判定。
#
# 判据（严格版）：
#   1. App 进程还活着
#   2. 点完之后**已经离开设置首页**——首页底部四个 tab 的 content-desc 必须消失；
#      不消失就说明这一点根本没打开任何页面（上一版只看“文字签名变了”，
#      被分组标题「网络」吸收点击、列表滚动一下就误判成 OK，已废掉）
#   3. 截图存证，人能复核
#
# 点击目标改用**副标题**而不是标题：设置里的分组标题和条目名会重名（「网络」），
# 按标题点会先命中不可点的分组标题。副标题（如「连通性 · DNS」）在整张列表里唯一。
set -u
APK="$1"
OUT="${2:-audit}"
PKG=com.destiny.mika   # applicationId; activity classes stay in com.openminis.app
HERE="$(cd "$(dirname "$0")" && pwd)"
ACT="$PKG/com.openminis.app.MainActivity"

mkdir -p "$OUT"
: > "$OUT/audit.tsv"
fail=0

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot: $1 ($(stat -c%s "$OUT/$1.png" 2>/dev/null || echo 0) bytes)"; }
alive() { [ -n "$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')" ]; }
tap() { python3 "$HERE/tap.py" "$@"; }
# 7 列：入口 / 截图 / 判定 / 说明 / 文本节点数 / 可点节点数 / 备注
row() { printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" "${5:-}" "${6:-}" "${7:-}" >> "$OUT/audit.tsv"; }

# One uiautomator dump, then three cheap queries over the saved file.
snap() {
  adb shell uiautomator dump /sdcard/audit.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/audit.xml > "$OUT/.ui.xml" 2>/dev/null
  [ -s "$OUT/.ui.xml" ]
}
sig() { tr '>' '\n' < "$OUT/.ui.xml" | grep -o 'text="[^"]*"' | md5sum | cut -d' ' -f1; }
# 页面里有多少个非空文本、多少个 clickable=true —— 用来判断"这页是不是空壳"
ntext() { tr '>' '\n' < "$OUT/.ui.xml" | grep -o 'text="[^"]*"' | grep -vc 'text=""' || true; }
nclick() { tr '>' '\n' < "$OUT/.ui.xml" | grep -c 'clickable="true"' || true; }
has() { tr '>' '\n' < "$OUT/.ui.xml" | grep -q "text=\"$1\""; }
# Still on the V7 home? The bottom tab bar is content-desc based and always shows
# all four tabs, so >=3 of them present is an unambiguous "we never left" signal.
# (Requiring 1 could false-positive on a sub-page that happens to label a node 对话.)
on_home() {
  local c
  c="$(tr '>' '\n' < "$OUT/.ui.xml" | grep -cE 'content-desc="(对话|会话|任务|动态|更多)"')"
  [ "${c:-0}" -ge 3 ]
}

# [destiny/audit-locale] The app is bilingual (t(zh,en) picks by activity
# locale) and V7's own shell is hardcoded Chinese, so an en-US emulator shows a
# half-Chinese/half-English blend. ci_lang/ci_theme seed the prefs the
# Appearance screen uses; pinning them here makes the audit reflect what a
# Chinese user actually sees.
CI_EXTRAS="--es ci_lang zh-CN --ei ci_theme 2"
launch() {
  adb shell am start -W -n "$ACT" $CI_EXTRAS > /dev/null 2>&1
  sleep "${1:-10}"
}

# Settings is no longer a bottom tab (75f41ac deduped More): it is a row on
# the 更多 tab. Get back to the settings hub from anywhere.
resync_settings() {
  local i=0
  # Already on the hub? A unique static subtitle proves it.
  if snap && has "云天 / 夜空、浅色深色、照片或纯色"; then return 0; fi
  while [ $i -lt 4 ]; do
    if snap && tap desc "更多" > /dev/null 2>&1; then
      sleep 2
      if tap text "设置" > /dev/null 2>&1; then sleep 2; return 0; fi
    fi
    adb shell input keyevent KEYCODE_BACK; sleep 2
    i=$((i + 1))
  done
  adb shell am force-stop "$PKG"; sleep 2
  launch 14
  tap desc "更多" > /dev/null 2>&1 || true
  sleep 2
  tap text "设置" > /dev/null 2>&1 || true
  sleep 3
}

# Scroll the settings list until the subtitle is rendered; never taps.
scroll_to() {
  local sub="$1" tries=0
  while [ $tries -lt 10 ]; do
    snap && has "$sub" && return 0
    adb shell input swipe 540 1800 540 1050 260; sleep 1
    tries=$((tries + 1))
  done
  return 1
}

echo "== install"
adb install -r -g "$APK" > "$OUT/install.txt" 2>&1 || true
grep -q Success "$OUT/install.txt" || { echo "::error::install failed"; cat "$OUT/install.txt"; exit 1; }

echo "== launch"
adb logcat -c || true
adb shell settings put system screen_off_timeout 1800000 || true
adb shell svc power stayon true || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell pm clear "$PKG" > /dev/null 2>&1 || true
# Seeding prefs needs one launch; on API < 33 attachBaseContext reads them, so
# the process must be restarted for zh-CN + dark to actually apply.
launch 12
adb shell am force-stop "$PKG"; sleep 2
launch 22
# Prove the pin actually landed; the audit is worthless if every page still
# renders the en fallback of t(zh,en).
langline="$(adb shell run-as "$PKG" cat shared_prefs/appearance_prefs.xml 2>/dev/null | tr -d '\r' | grep -o 'app_language[^/]*' | head -1)"
echo "pinned prefs: ${langline:-<unreadable>}"
case "$langline" in
  *zh*) ;;
  *) echo "::warning::could not confirm zh-CN pin — screenshots may fall back to English" ;;
esac

if ! alive; then
  echo "::error::app is not running after launch"
  row "launch" "-" "DEAD" "app not running"
  exit 1
fi

echo "== tabs"
# HqTabBar now has five tabs (对话/会话/任务/动态/更多). 群聊 and 设置 moved
# into 更多; the old four-tab list made every later probe miss and burned the
# whole 60-minute job budget.
for t in 对话 会话 任务 动态 更多; do
  n="tab-$t"
  if tap desc "$t" > /dev/null 2>&1; then
    sleep 3
    shot "$n" 1
    snap || true
    if ! alive; then
      row "$t tab" "$n.png" "DEAD" "crash on tab switch"; fail=1
    else
      tx="$(ntext)"; ck="$(nclick)"
      if [ "${tx:-0}" -le 3 ] && [ "${ck:-0}" -le 1 ]; then
        row "$t tab" "$n.png" "EMPTY" "tab renders almost no content" "$tx" "$ck" "shell"; fail=1
      else
        row "$t tab" "$n.png" "OK" "-" "$tx" "$ck" ""
      fi
    fi
  else
    row "$t tab" "-" "MISSING" "tab label not found"; fail=1
  fi
done

echo "== settings entries"
# 标签|副标题 —— 副标题唯一，用它定位可避免命中同名的分组标题。
# Aligned with MibotSettingsScreen.kt (2026-11-27). Only static subtitles are
# used: rows with a live count (账号台账 / 服务商 / 邮箱台账 / 模型) would never
# match the exact-text lookup in tap.py.
ENTRIES=(
  "AI 网关|账号池、故障切换、冷却与健康检查"
  "模型组|模型别名与回退/负载均衡"
  "AI 邮箱|能发信的邮箱 · 自动化等级、撤销窗口、发信记录"
  "背景与主题|云天 / 夜空、浅色深色、照片或纯色"
  "Bots 与子代理|保存的 Bot 可被主 AI 当作子代理调用"
  "人格|主 AI 的性格与说话方式"
  "后台操控 / 虚拟屏|在隐藏虚拟屏操作 App，不占你的屏幕 · 无线调试 / Shizuku"
  "沙箱 / 运行环境|Alpine Linux · 终端、文件、DNS、重置"
  "服务器|经 Tailscale 管理你的机器 · 只存清单，巡检交给 Agent"
  "语音|按住说 · 停 2 秒就发 · 免提连说 · 朗读"
  "全部页面|56 屏设计规格，全部原生页面，可逐行交给 Agent"
  "权限与自动化|已授予什么、实际用来做什么 · 自动化等级"
  "诊断|日志、测试与上报"
  "反馈问题|打开已填好设备信息的问题单"
  "关于 Omega|版本、更新与开源许可（GPL-3.0）"
)
resync_settings
i=0
for e in "${ENTRIES[@]}"; do
  i=$((i + 1))
  label="${e%%|*}"
  sub="${e#*|}"
  idx="$(printf '%02d' "$i")"
  n="set-$idx"
  if ! scroll_to "$sub"; then
    row "$label" "-" "MISSING" "row subtitle never rendered: $sub"
    fail=1
    resync_settings
    continue
  fi
  before="$(sig)"
  if tap text "$sub" > /dev/null 2>&1; then
    sleep 4
    shot "$n" 2
    snap || true
    after="$(sig)"
    if ! alive; then
      row "$label" "$n.png" "DEAD" "crash opening entry"; fail=1
    elif on_home; then
      row "$label" "$n.png" "NOCHANGE" "still on settings home — tap opened nothing"; fail=1
    elif [ "$after" = "$before" ]; then
      row "$label" "$n.png" "NOCHANGE" "screen identical before/after"; fail=1
    else
      tx="$(ntext)"; ck="$(nclick)"
      # A legit empty-state page still carries a title and controls (e.g.
      # "No scheduled tasks yet" + back/menu/FAB = 2 texts / 3 clickables).
      # An unwired shell has neither. Require BOTH counts to be tiny.
      if [ "${tx:-0}" -le 3 ] && [ "${ck:-0}" -le 1 ]; then
        row "$label" "$n.png" "EMPTY" "opened but renders almost no content" "$tx" "$ck" "shell"
        fail=1
      elif has "$label"; then
        row "$label" "$n.png" "OK" "-" "$tx" "$ck" ""
      else
        row "$label" "$n.png" "OK" "opened, title differs from row label" "$tx" "$ck" ""
      fi
    fi
  else
    row "$label" "-" "MISSING" "tap failed on subtitle: $sub"; fail=1
  fi
  adb shell input keyevent KEYCODE_BACK; sleep 2
  if ! alive; then launch 14; fi
  resync_settings
done

adb logcat -d -b crash > "$OUT/crash.txt" 2>/dev/null || true

echo "== summary"
awk -F'\t' '{c[$3]++} END {for (k in c) printf "%s=%d ", k, c[k]; print ""}' "$OUT/audit.tsv"
cat "$OUT/audit.tsv"
[ "$fail" -eq 0 ] || echo "::warning::some screens did not open (see audit.tsv)"
exit 0
