param(
    [Parameter(Mandatory = $true)][string]$RunId,
    [string]$OutDir = "E:\omega\artifacts\final-check"
)

$ErrorActionPreference = 'Stop'
$adb = "E:\Apps\ApkStart\installed\resources\runtime\platform-tools\adb.exe"
$env:ANDROID_ADB_SERVER_PORT = "5037"

# Authenticated GitHub API helper (git credential manager holds the PAT).
$credText = "protocol=https`nhost=github.com`n`n" | & git -c credential.helper=manager credential fill
$cred = @{}
$credText | ForEach-Object { if ($_ -match '^([^=]+)=(.*)$') { $cred[$Matches[1]] = $Matches[2] } }
$h = @{'User-Agent'='opencode'; 'Authorization'="Bearer $($cred.password)"}

# Wait for the APK artifact on this run.
$apkArtifact = $null
for ($i = 0; $i -lt 60; $i++) {
    $a = Invoke-RestMethod -Uri "https://api.github.com/repos/ddfc1991/omega-opencode-build/actions/runs/$RunId/artifacts?per_page=100" -Headers $h
    $apkArtifact = $a.artifacts | Where-Object { $_.name -eq 'minis-debug-apk' } | Select-Object -First 1
    if ($apkArtifact) { break }
    Start-Sleep -Seconds 30
}
if (-not $apkArtifact) { throw "No minis-debug-apk artifact for run $RunId" }

$zip = Join-Path $OutDir "apk-$RunId.zip"
$extract = Join-Path $OutDir "apk-$RunId"
Invoke-WebRequest -Uri $apkArtifact.archive_download_url -Headers $h -OutFile $zip
Expand-Archive -Force -Path $zip -DestinationPath $extract
$apk = Join-Path $extract "minis-debug.apk"
$hash = (Get-FileHash -Algorithm SHA256 -Path $apk).Hash
Write-Output "APK=$apk SHA256=$hash"

# Install and launch into HQ home (dark), then open the More tab.
& $adb -s emulator-5554 install -r $apk | Out-Null
& $adb -s emulator-5554 shell am force-stop com.destiny.mika
& $adb -s emulator-5554 shell am start -n com.destiny.mika/com.openminis.app.MainActivity --ez glass_demo true --es ci_lang zh-CN --ei ci_theme 2 | Out-Null
Start-Sleep -Seconds 14

# Dismiss any "System UI isn't responding" dialog from the slow emulator.
& $adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml | Out-Null
$xml = & $adb -s emulator-5554 shell cat /sdcard/ui.xml
if ($xml -match "System UI isn't responding") {
    $m = [regex]::Match($xml, 'text="Wait"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if ($m.Success) {
        $x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
        $y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
        & $adb -s emulator-5554 shell input tap $x $y
        Start-Sleep -Seconds 4
    }
}

# More tab lives at the far right of the bottom bar.
& $adb -s emulator-5554 shell input tap 1239 2742
Start-Sleep -Seconds 4
& $adb -s emulator-5554 exec-out screencap -p > (Join-Path $OutDir "04-dark-more-deduped.png")
Write-Output "saved: $(Join-Path $OutDir '04-dark-more-deduped.png')"

# Light pass.
& $adb -s emulator-5554 shell am force-stop com.destiny.mika
& $adb -s emulator-5554 shell am start -n com.destiny.mika/com.openminis.app.MainActivity --ez glass_demo true --es ci_lang zh-CN --ei ci_theme 1 | Out-Null
Start-Sleep -Seconds 14
& $adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml | Out-Null
$xml = & $adb -s emulator-5554 shell cat /sdcard/ui.xml
if ($xml -match "System UI isn't responding") {
    $m = [regex]::Match($xml, 'text="Wait"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if ($m.Success) {
        $x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
        $y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
        & $adb -s emulator-5554 shell input tap $x $y
        Start-Sleep -Seconds 4
    }
}
& $adb -s emulator-5554 shell input tap 1239 2742
Start-Sleep -Seconds 4
& $adb -s emulator-5554 exec-out screencap -p > (Join-Path $OutDir "05-light-more-deduped.png")
Write-Output "saved: $(Join-Path $OutDir '05-light-more-deduped.png')"
