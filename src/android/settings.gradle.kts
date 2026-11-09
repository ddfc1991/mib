pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // [T-android-vad] RealTimeCutVADLibraryForAndroid ships via JitPack
        // only. Same author and same underlying stack (Silero + ONNX Runtime +
        // WebRTC APM) as the RealTimeCutVADLibrary SPM package iOS already
        // uses, so both platforms segment speech with the same model and the
        // same tunables.
        maven { url = uri("https://jitpack.io") }
        // rclone.aar — the backup feature's remote destinations (SMB / WebDAV /
        // SFTP / S3 / FTP). Not published to any Maven repo: it is built from
        // deps/rclone-mobile by `deps/build_rclone_android.sh`, which is also
        // what produces the iOS XCFramework from the same Go sources and the
        // same trimmed backend list. Treated as a build artifact, not a vendored
        // binary — see docs/backup-restore-design.md §6.2.
        flatDir { dirs("app/libs") }
    }
}

rootProject.name = "Minis"
include(":app")
// [fork/mibot-merge] Mika browser port: the pure-JVM `core` module that backs
// com.mibot.core.cap.web (CDP transport, WebSocket, NetworkLedger, page
// scripts) and com.mibot.core.uistate. The app module's com.mibot.cap.web
// sources consume it via implementation(project(":core")).
include(":core")
