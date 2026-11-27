param(
    [string]$Out = "E:\omega\artifacts\emu.png"
)

# ApkStart 的 adb 走 5044，和本机 5037 完全隔离，不会碰到用户手机。
$env:ANDROID_ADB_SERVER_PORT = "5044"
$adb = "adb"

Write-Output "--- devices on 5044 ---"
& $adb devices 2>&1 | Write-Output
$dev = (& $adb devices 2>&1 | Select-String 'emulator-\d+') | Select-Object -First 1
if (-not $dev) { Write-Output "ERROR: no ApkStart emulator"; exit 1 }
$id = ($dev.ToString() -split '\s+')[0]
& $adb -s $id shell screencap -p /sdcard/apkstart-shot.png 2>&1 | Write-Output
& $adb -s $id pull /sdcard/apkstart-shot.png $Out 2>&1 | Write-Output
Get-Item $Out | Select-Object FullName, Length
