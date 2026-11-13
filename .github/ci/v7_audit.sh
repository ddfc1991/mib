#!/usr/bin/env bash
# [destiny/v7-audit] 逐项体检：把 V7 的 4 个 tab 和 设置里 29 个入口逐个点开，
# 每一屏都截图 + 记录 App 是否还活着、这一屏到底渲染出东西没有。
#
# 用法: bash .github/ci/v7_audit.sh <apk> <outdir>
# 产出: <outdir>/*.png  逐屏截图
#       <outdir>/audit.tsv  每一行的判定
set -u
APK="$1"
OUT="${2:-audit}"
PKG=com.openminis.app
TAP="python3 .github/ci/tap.py"

mkdir -p "$OUT"
: > "$OUT/audit.tsv"
fail=0

shot() { # name, settle seconds
  sleep "${2:-3}"
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "shot: $1 ($(stat -c%s "$OUT/$1.png") bytes)"
}

pid() { adb shell pidof "$PKG" 2>/dev/null | tr -d '\r'; }

alive() { [ -n "$(pid)" ]; }

# uiautomator dump of the current screen, stripped to a text signature, so we can
# tell "opened a different page" from "tap did nothing / bounced back".
sig() {
  adb shell uiautomator dump /sdcard/audit.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/audit.xml 2>/dev/null \
    | tr '>' '\n' | grep -o 'text="[^"]*"' | md5sum | cut -d' ' -f1
}

row() { # label, shot, status, note
  printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" >> "$OUT/audit.tsv"
}

echo "== install"
adb install -r -d "$APK" > "$OUT/install.txt" 2>&1 || { echo "::error::install failed"; cat "$OUT/install.txt"; exit 1; }

echo "== launch"
adb shell pm clear "$PKG" >/dev/null 2>&1 || true
adb shell am start -W -n "$PKG/com.openminis.app.MainActivity" \
  --es ci_lang zh-CN --ez ci_vault_demo true > "$OUT/start.txt" 2>&1
sleep 14

if ! alive; then
  echo "::error::app is not running after launch"
  row "launch" "-" "DEAD" "app not running"
  exit 1
fi

echo "== tabs"
for t in 对话 动态 群聊 设置; do
  before="$(sig)"
  if $TAP desc "$t" > /dev/null 2>&1; then
    sleep 3
    n="tab-$t"
    shot "$n" 1
    after="$(sig)"
    if ! alive; then
      row "$t tab" "$n.png" "DEAD" "crash on tab switch"
      fail=1
    elif [ "$before" = "$after" ]; then
      row "$t tab" "$n.png" "NOCHANGE" "tap did not change the screen"
      fail=1
    else
      row "$t tab" "$n.png" "OK" "-"
    fi
  else
    row "$t tab" "-" "MISSING" "tab label not found"
    fail=1
  fi
done

# Back to settings for the entry sweep.
$TAP desc "设置" > /dev/null 2>&1 || true
sleep 3

# The settings list is 29 rows long; only ~10 fit on screen. Walk forward with
# swipes instead of assuming the row is visible.
tap_scrolled() {
  local label="$1" tries=0
  while [ $tries -lt 8 ]; do
    if $TAP text "$label" > /dev/null 2>&1; then return 0; fi
    adb shell input swipe 540 1800 540 1050 320; sleep 1
    tries=$((tries + 1))
  done
  return 1
}

echo "== settings entries"
# Exactly the labels v7SettingsCatalog() renders, in order.
ENTRIES=(
  "模型服务商" "模型组" "用量统计"
  "子 Agent" "技能" "记忆" "MCP 服务器" "环境变量"
  "语音" "手机控制" "微信" "任务队列" "钱包"
  "存储" "根文件系统" "挂载目录" "文件浏览器" "备份"
  "网络" "代理" "Tailscale" "容器 DNS"
  "外观" "背景" "权限" "日志" "定时任务" "关于"
)
i=0
for label in "${ENTRIES[@]}"; do
  i=$((i + 1))
  idx="$(printf '%02d' "$i")"
  before="$(sig)"
  if tap_scrolled "$label"; then
    sleep 4
    n="set-$idx-$i"
    shot "$n" 1
    after="$(sig)"
    if ! alive; then
      row "$label" "$n.png" "DEAD" "crash opening entry"
      fail=1
    elif [ "$before" = "$after" ]; then
      row "$label" "$n.png" "NOCHANGE" "tap did not open anything"
      fail=1
    else
      row "$label" "$n.png" "OK" "-"
    fi
    # Return: one BACK normally, a second one if a dialog swallowed the first.
    adb shell input keyevent KEYCODE_BACK; sleep 2
    if ! alive; then
      adb shell am start -n "$PKG/com.openminis.app.MainActivity" >/dev/null 2>&1; sleep 6
    fi
  else
    row "$label" "-" "MISSING" "row label not on the settings page"
    fail=1
  fi
  # Resync to the settings tab (scroll position may have moved).
  $TAP desc "设置" > /dev/null 2>&1 || true
  sleep 1
done

# A crash log is worth more than the verdict alone.
adb logcat -d -b crash > "$OUT/crash.txt" 2>/dev/null || true

echo "== summary"
awk -F'\t' '{c[$3]++} END {for (k in c) printf "%s=%d ", k, c[k]; print ""}' "$OUT/audit.tsv"
cat "$OUT/audit.tsv"
[ "$fail" -eq 0 ] || echo "::warning::some screens did not open (see audit.tsv)"
exit 0
