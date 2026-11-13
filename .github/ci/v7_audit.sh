#!/usr/bin/env bash
# [destiny/v7-audit] 逐项体检：4 个 tab + 设置里 29 个入口逐个点开，截图 + 判定。
# 判据：App 还活着、这一屏和点之前不是同一屏（文本签名不同）、没有 crash。
# 用法: bash .github/ci/v7_audit.sh <apk> <outdir>   产出 <outdir>/audit.tsv
set -u
APK="$1"
OUT="${2:-audit}"
PKG=com.destiny.mika   # applicationId; activity classes stay in com.openminis.app
HERE="$(cd "$(dirname "$0")" && pwd)"
ACT="$PKG/com.openminis.app.MainActivity"

mkdir -p "$OUT"
: > "$OUT/audit.tsv"
fail=0

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot: $1 ($(stat -c%s "$OUT/$1.png") bytes)"; }
alive() { [ -n "$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')" ]; }
tap() { python3 "$HERE/tap.py" "$@"; }
row() { printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" >> "$OUT/audit.tsv"; }

dump() { adb shell uiautomator dump /sdcard/audit.xml >/dev/null 2>&1; adb exec-out cat /sdcard/audit.xml 2>/dev/null; }
# md5 of every text= attribute currently rendered — changes when the page changes.
sig() { dump | tr '>' '\n' | grep -o 'text="[^"]*"' | md5sum | cut -d' ' -f1; }
# Is this exact label on screen right now?
has() { dump | tr '>' '\n' | grep -q "text=\"$1\""; }

launch() {
  adb shell am start -W -n "$ACT" --es ci_lang zh-CN > /dev/null 2>&1
  sleep "${1:-10}"
}

# Get back to the 设置 tab from anywhere: tab bar if we are on a V7 page,
# BACK if we are on a pushed sub-page, relaunch as the last resort.
resync_settings() {
  local i=0
  while [ $i -lt 3 ]; do
    if tap desc "设置" > /dev/null 2>&1; then sleep 2; return 0; fi
    adb shell input keyevent KEYCODE_BACK; sleep 2
    i=$((i + 1))
  done
  adb shell am force-stop "$PKG"; sleep 2
  launch 12
  tap desc "设置" > /dev/null 2>&1 || true
  sleep 3
}

# Scroll the settings list until the label is rendered; never taps.
scroll_to() {
  local label="$1" tries=0
  while [ $tries -lt 8 ]; do
    has "$label" && return 0
    adb shell input swipe 540 1750 540 1100 280; sleep 1
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
launch 25

if ! alive; then
  echo "::error::app is not running after launch"
  row "launch" "-" "DEAD" "app not running"
  exit 1
fi

echo "== tabs"
HOME_SIG="$(sig)"
for t in 对话 动态 群聊 设置; do
  if tap desc "$t" > /dev/null 2>&1; then
    sleep 3
    n="tab-$t"
    shot "$n"
    after="$(sig)"
    if ! alive; then
      row "$t tab" "$n.png" "DEAD" "crash on tab switch"; fail=1
    elif [ "$t" != "对话" ] && [ "$after" = "$HOME_SIG" ]; then
      row "$t tab" "$n.png" "NOCHANGE" "tap did not change the screen"; fail=1
    else
      row "$t tab" "$n.png" "OK" "-"
    fi
  else
    row "$t tab" "-" "MISSING" "tab label not found"; fail=1
  fi
done

echo "== settings entries"
ENTRIES=(
  "模型服务商" "模型组" "用量统计"
  "子 Agent" "技能" "记忆" "MCP 服务器" "环境变量"
  "语音" "手机控制" "微信" "任务队列" "钱包"
  "存储" "根文件系统" "挂载目录" "文件浏览器" "备份"
  "网络" "代理" "Tailscale" "容器 DNS"
  "外观" "背景" "权限" "日志" "定时任务" "关于"
)
resync_settings
i=0
for label in "${ENTRIES[@]}"; do
  i=$((i + 1))
  idx="$(printf '%02d' "$i")"
  n="set-$idx"
  if ! scroll_to "$label"; then
    row "$label" "-" "MISSING" "row label never rendered"
    fail=1
    resync_settings
    continue
  fi
  before="$(sig)"
  if tap text "$label" > /dev/null 2>&1; then
    sleep 4
    shot "$n"
    after="$(sig)"
    if ! alive; then
      row "$label" "$n.png" "DEAD" "crash opening entry"; fail=1
    elif [ "$after" = "$before" ]; then
      row "$label" "$n.png" "NOCHANGE" "tap opened nothing"; fail=1
    else
      row "$label" "$n.png" "OK" "-"
    fi
  else
    row "$label" "-" "MISSING" "tap failed"; fail=1
  fi
  adb shell input keyevent KEYCODE_BACK; sleep 2
  if ! alive; then launch 12; fi
  resync_settings
done

adb logcat -d -b crash > "$OUT/crash.txt" 2>/dev/null || true

echo "== summary"
awk -F'\t' '{c[$3]++} END {for (k in c) printf "%s=%d ", k, c[k]; print ""}' "$OUT/audit.tsv"
cat "$OUT/audit.tsv"
[ "$fail" -eq 0 ] || echo "::warning::some screens did not open (see audit.tsv)"
exit 0
