package com.mibot.ui

import android.app.Activity

/**
 * Mika 的 launcher Activity 桩:不搬 Mika 的 ui/MainActivity.kt(与 Mibot 的启动 Activity 冲突)。
 * cap 只把 [com.mibot.cap.phone.setup.PairingNotifier] 的「打开配对页」Intent 指向它;
 * Mibot 侧应把 [Intent] 指向自己的设置页(见 MIKA_PORT_REPORT 的 manifest 清单)。
 */
class MainActivity : Activity()
