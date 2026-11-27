# AGENTS.md — Omega（Mibot fork）

给接手本仓库的 OpenCode 会话。先读完「验证闭环」和「已知坑」再动手。

## 1. 这是什么

上游是 [OpenMinis](https://github.com/OpenMinis/OpenMinis)（GPLv3，端上 AI agent，自带 Alpine 沙箱）。
本仓库是 Android 侧 fork，已改包名和品牌：

| 项 | 值 |
|---|---|
| applicationId | `com.destiny.mika` |
| versionName / versionCode | `1.26` / `48` |
| compileSdk / targetSdk / minSdk | 36 / 35 / 26 |
| ABI | 仅 `arm64-v8a` |
| 产物 | `:app`（主 App）+ `:mibot-life`（独立生活 App，`com.mibot.life`） |

`com.openminis.app.*` 是上游主壳（聊天、provider、Room、OpenCode 客户端）；
`com.mibot.*` 是 fork 自己加的能力。改代码前先判断落在哪一侧。

## 2. 目录地图

```
src/android/
  app/src/main/java/com/openminis/app/     上游主壳
    opencode/                              OpenCode 客户端（Api/Store/Models/Screens/ChatScreen）
    provider/                              provider 接入（含 OpenCodeSessionHeader）
    data/db/                               Room；schema JSON 在 app/schemas/
  app/src/main/java/com/mibot/             fork 能力
    cap/phone/{a11y,display,shell,setup,sms}   手机操控（无障碍、虚拟屏、shell、配对）
    cap/web/cdp/                           CDP 浏览器通道
    cap/sandbox/                           沙箱桥接
    pocket/ proactive/ vision/ wechat/     生活/主动/视觉/微信
    ui/{hq,components,theme}               HQ 玻璃 UI、AppTokens、AppTopBar
  core/                                    纯 JVM：CDP transport、WebSocket、NetworkLedger、uistate
  mibot-life/                              独立生活 App（复用 :core）
docs/specs/                                debug-server-api、url scheme 等
docs/design-preview/                       设计稿 HTML + 截图（本轮已入库）
.github/ci/                                smoke.sh、v7_audit.sh、locale 检查
deps/                                      proot / rclone / ffmpeg 等原生依赖源码
```

## 3. 分支与远端

| 远端 | 地址 | 用途 |
|---|---|---|
| `origin` | `ddfc1991/omega` | 源码主线 |
| `build` | `ddfc1991/omega-opencode-build` | CI 构建镜像；push 触发 GitHub Actions |

- 当前交接分支：`feat/omega-bot`（从 `feat/opencode-client` 的 `00d1550` 拉出）。
- `feat/opencode-client` 本地比 `origin/feat/opencode-client` 多 8 个提交，尚未推回 origin。
- CI 只在 `main` / `dev` / `feat/**` 上跑。

## 4. 构建

### 4.1 本机不要直接 `assembleDebug`

这台 Windows 机器没配 `ANDROID_HOME` / `ANDROID_NDK_HOME`，且原生依赖产物缺失：
`app/libs/rclone.aar`、`assets/proot-aarch64`、`assets/alpine-minirootfs.tar.gz` 都不在。
本地构建会在 `:app:compileGithubDebugAidl` 报 `Could not find :rclone:`（见 `build-opencode.log`）。

### 4.2 走 CI（推荐）

```powershell
cd E:\omega
git push build HEAD:refs/heads/feat/omega-bot
```

流水线（`.github/workflows/android.yml`）：

1. **build** — `:app:assembleDebug` + `:mibot-life:assembleDebug`，上传 artifact `minis-debug-apk`（14 天）。
2. **smoke** — API 30 / 31 模拟器跑 `.github/ci/smoke.sh`，截图作为 artifact。
3. **audit** — API 31 跑 `.github/ci/v7_audit.sh`，逐个点开 29 个入口并产出 `audit.tsv`。

下载 CI 产物：`scripts/capture-ci-artifact.ps1 -RunId <run>`。
默认下载 `minis-debug-apk` 并装到 `emulator-5554` 做明暗双截图；拿 smoke/audit 用
`-ArtifactName v7-audit-api31 -DownloadOnly`（按当轮改坐标和截图名）。
脚本用 git credential manager 里的 PAT 调 GitHub API。单张模拟器截图：`scripts/emu-screenshot.ps1`。

### 4.3 必须本机构建时

按 `BUILDING.md` 先补原生依赖，再构建：

```powershell
cd E:\omega
./deps/build_proot.sh
./scripts/prepare_android_sandbox.sh
./deps/build_rclone_android.sh
mkdir -p src/android/app/libs
cp deps/build/rclone/rclone.aar src/android/app/libs/
cd src/android
./gradlew :app:assembleDebug
```

需要 JDK 17、NDK r28+、CMake 3.22.1；Gradle 由 wrapper 提供（8.11.1）。

## 5. 验证闭环（硬要求）

「能启动」不算验证，必须断言具体行为：

1. 装 APK → `adb -s emulator-5554 install -r minis-debug.apk`
2. 冷启动并指定主题/语言：
   `am start -n com.destiny.mika/com.openminis.app.MainActivity --ez glass_demo true --es ci_lang zh-CN --ei ci_theme 1`
   （`ci_theme 1` 亮色，`2` 暗色）
3. 截图 + `uiautomator dump`，两份都留：`screencap -p` 与 `uiautomator dump /sdcard/ui.xml`
4. 证据放到 `artifacts/final-check/<批次>/`，文件名带序号和状态（参考 `v3/`）

本机 `adb` 在 PATH（chocolatey，端口 5037）。ApkStart 自带模拟器走 5044
（`$env:ANDROID_ADB_SERVER_PORT="5044"`），两套 adb server 互不干扰，别混用。
脚本里旧路径 `E:\Apps\ApkStart\...\adb.exe` 已失效，统一用 PATH 里的 `adb`。

最近一批证据：`artifacts/final-check/v3/`（本机，未入库；11/26，deep-link 冷启动、发消息前后 UI、明暗 More/设置）。

## 6. 代码约定

- Kotlin + Compose；颜色、圆角、间距走 `AppTokens`，顶栏用 `AppTopBar`，不硬编码色值。
- 新文案同时补 `zh` 与 `zh-rTW`；CI 会跑 `check_new_locale_keys.py`。
- Room 改动必须提交 `app/schemas/.../<version>.json`（`MigrationTestHelper` 依赖它）。
- 单元测试：`./gradlew :app:testDebugUnitTest --tests '...'`（`testDebugUnitTest` 是 `githubDebug` 的别名）。
- 仪器测试：`./gradlew :app:connectedAndroidTest`，需要设备/模拟器。
- 提交信息：`fix(scope): ...` / `feat(scope): ...`，一个提交只做一件事。

## 7. 已知坑

- **submodule 未初始化**：`deps/ish`、`deps/proot` 当前都是 `-` 状态。Android 只需
  `git submodule update --init --depth 1 deps/proot`。
- **沙箱假健康**：命令全部返回 `[Shell not running] (exit code: -1)` 通常是 APK 里缺
  `libproot-loader.so`。验证方式是「跑一条命令断言 exit 0」，不是看沙箱启动日志。
- **大文件**：`artifacts/` 约 2.4 GB，含多个 >100 MB 的 APK zip，已加进 `.gitignore`，不要 `git add -f`。
- **模拟器卡顿**：会弹 "System UI isn't responding"；截图脚本里已有自动点 Wait 的逻辑，复用即可。
- **聊天 FK 崩溃**：`artifacts/crash-20261125/` 里的
  `SQLiteConstraintException: FOREIGN KEY constraint failed` 已由 `a0e1d0c`
  （repair missing session row before message insert）修复，那批日志是修复前的，不要重复排查。
- **provider 定制文件**：`src/android/app/provider-customization.properties` 不入库，从
  `.example` 复制即可；留空能编译，只有对应功能运行时报错。
- **CI 签名可能不是固定 key**：`build` 远端若没配 `MIBOT_KEYSTORE_B64`，产物用 runner 的
  debug key（`signing.txt` 里 `CN=Android Debug`）。这种包无法覆盖安装用户机上已有的
  Mibot，验证请走 CI 模拟器；需要覆盖安装就得给 build repo 配 keystore secrets。

## 8. 当前状态

`feat/omega-bot` 的 HEAD 包含（相对 `origin/feat/opencode-client` 的 8 个提交）：

- `00d1550` deep-link：每次启动只消费一次 pending action
- `2c3ec72` UI：在 composition 内解析 live token getter
- `bd20f49` deep-link：session / terminal / settings 作为冷启动路由挂载
- `51e9ec0` 亮色 token 全量清扫 + 核心页面磨砂
- `f8bd33b` 设置 hub 行统一为 HQ 玻璃样式
- `cc074d0` 聊天页玻璃遮罩 + 磨砂 composer
- `406342b` 全站玻璃扫尾（半透明 page veil + 玻璃设置分组）
- `a0e1d0c` 聊天：插入消息前修复缺失的 session 行

本轮交接额外提交：`docs/design-preview/` 入库、`artifacts/` 加入 `.gitignore`、本文件、
helper 脚本，以及修掉过期的 CI audit 基线。

`feat/omega-bot` 已推到 `origin` 与 `build` 两个远端。

CI 记录（build repo `ddfc1991/omega-opencode-build`）：

- run `45927569571` @ `914cdd9`：build ✅、smoke API 30 ✅、smoke API 31 ✅；
  V7 audit ⏳ 跑满 60 min 后被 timeout 取消。根因是脚本还在找已经不存在的
  「群聊 / 设置」底栏 tab（见 `75f41ac` 的 dedupe），28 个入口全 MISSING。
- `4df94b8` 把 audit 基线对齐当前 `HqTabBar` 五个 tab，设置改从「更多」进入，
  只取 `MibotSettingsScreen.kt` 里静态副标题的 17 行，audit timeout 提到 90 min。
- run `45934814669` @ `4df94b8` 为重跑，结果以它为准。

下一步建议：

1. 等 run `45934814669` 结束，拉 `v7-audit-api31` / smoke 产物复核（脚本见 `scripts/capture-ci-artifact.ps1`）。
2. OpenCode 客户端（`com.openminis.app.opencode`）还缺多会话与深链的端到端回归。
3. CI 全绿后合回 `origin/feat/opencode-client` 或开 PR。

## 9. 外部参考

- Mika 参考克隆：`E:\AI\archive\WorkBuddy\2026-11-04-08-41-34\mika-src`
  （`github.com/wangzan101/Mika`，shallow，`main` @ `da977fa`）。
- 移植说明：同目录 `mika-port/INTEGRATION.md`（a11y 屏幕层 / MCP / 沙箱三块缺口）。
- 文件级快照（无 .git）：`E:\AI\archive\WorkBuddy\2026-11-05-08-14-33\mika-source`。
