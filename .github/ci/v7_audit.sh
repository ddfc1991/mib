#!/usr/bin/env bash
# [destiny/v7-audit] 逐项体检：4 个 tab + 设置里 28 个入口逐个点开，截图 + 判定。
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
  c="$(tr '>' '\n' < "$OUT/.ui.xml" | grep -cE 'content-desc="(对话|动态|群聊|设置)"')"
  [ "${c:-0}" -ge 3 ]
}

launch() {
  adb shell am start -W -n "$ACT" --es ci_lang zh-CN > /dev/null 2>&1
  sleep "${1:-10}"
}

# Get back to the 设置 tab from anywhere: tab bar if we are on a V7 page,
# BACK if we are on a pushed sub-page, relaunch as the last resort.
resync_settings() {
  local i=0
  while [ $i -lt 4 ]; do
    if snap && tap desc "设置" > /dev/null 2>&1; then sleep 2; return 0; fi
    adb shell input keyevent KEYCODE_BACK; sleep 2
    i=$((i + 1))
  done
  adb shell am force-stop "$PKG"; sleep 2
  launch 14
  tap desc "设置" > /dev/null 2>&1 || true
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
launch 25

if ! alive; then
  echo "::error::app is not running after launch"
  row "launch" "-" "DEAD" "app not running"
  exit 1
fi

echo "== tabs"
for t in 对话 动态 群聊 设置; do
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
ENTRIES=(
  "模型服务商|API Key · 账号 · 余额"
  "模型组|主模型 · 备用 · 路由顺序"
  "用量统计|token · 调用次数 · 花费"
  "子 Agent|派单用的 Agent 花名册"
  "技能|装在设备上的技能包"
  "记忆|长期记忆与文件"
  "MCP 服务器|外部工具接入"
  "环境变量|注入到运行时的变量"
  "语音|转写 · 朗读 · 唤醒"
  "手机控制|无障碍 · 屏幕 · 点击"
  "微信|未接入 · 不显示假账号"
  "任务队列|真实作业 · 执行中 · 排队"
  "钱包|未接入 · 不显示假余额"
  "存储|占用 · 清理"
  "根文件系统|容器镜像"
  "挂载目录|宿主机目录映射"
  "文件浏览器|浏览容器内文件"
  "备份|导出 · 导入 · 目标"
  "网络|连通性 · DNS"
  "代理|HTTP / SOCKS 代理"
  "Tailscale|组网与设备"
  "容器 DNS|容器内解析"
  "外观|主题 · 字体 · 图标"
  "背景|壁纸与模糊"
  "权限|系统权限总览"
  "日志|运行日志与诊断"
  "定时任务|Cron 与触发"
  "关于|版本 · 构建 · 许可"
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
