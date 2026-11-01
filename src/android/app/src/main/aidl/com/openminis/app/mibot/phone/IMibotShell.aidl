// Adapted from Mika's IMikaShell.aidl (https://github.com/wangzan101/Mika),
// Copyright 2026 wangzan101, Apache-2.0. Trimmed for Mibot (GPL-3.0-or-later).
package com.openminis.app.mibot.phone;

import android.os.Bundle;
import android.view.Surface;

// Privileged helper running as shell (uid 2000), started through local Wireless
// Debugging or Shizuku. Every method that touches a display only acts on displays
// this helper created itself; display 0 (the user's screen) is never a target.
interface IMibotShell {
    int createDisplay(in Surface surface, int width, int height, int dpi) = 1;
    void releaseDisplay(int displayId) = 2;
    // 0=started 1=app is in use on the main screen (not started) 2=no launcher entry 3=error 7=on another agent display
    int startApp(int displayId, String packageName) = 3;
    int topTaskId(int displayId) = 4;
    boolean injectKey(int displayId, int keyCode) = 6;
    boolean injectTap(int displayId, int x, int y) = 7;
    String topActivity(int displayId) = 8;
    boolean injectStroke(int displayId, in int[] xs, in int[] ys, in long[] times) = 9;
    // 0=set 1=user clipboard unreadable (left alone) 2=error
    int setClipText(String text) = 10;
    void restoreClip() = 11;
    // Ctrl+A / Ctrl+V only, own displays only
    boolean injectCtrlKey(int displayId, int keyCode) = 12;
    // Runs `sh -c command` as shell; returns exitCode + output
    Bundle runCommand(String command) = 13;
    int[] identity() = 14;
    void releaseAll() = 15;
    // Types ASCII text with key events on an own display
    boolean injectText(int displayId, String text) = 16;
    void destroy() = 16777114;
}
