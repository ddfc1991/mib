import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Mibot 生活 —— 独立 App（applicationId com.mibot.life）。
// 与 Mibot 共用 core（vd/流云体/记忆），但独立安装、独立图标。
// 免沙箱：生活系统 = 桌宠 + 生活引擎(Kotlin) + vd 操作 + 内容产出，无脚本运行需求。

android {
    namespace = "com.mibot.life"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mibot.life"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 复用 Mibot 的固定签名（CI MIBOT_KEYSTORE_B64），保证覆盖装不丢数据
    val mibotKeystore = System.getenv("MIBOT_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (mibotKeystore != null) {
            create("mibot") {
                storeFile = mibotKeystore
                storePassword = System.getenv("MIBOT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MIBOT_KEY_ALIAS") ?: "mibot"
                keyPassword = System.getenv("MIBOT_KEY_PASSWORD") ?: System.getenv("MIBOT_KEYSTORE_PASSWORD")
                storeType = "pkcs12"
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }
    val mibotSigning = if (mibotKeystore != null) signingConfigs.getByName("mibot") else signingConfigs.getByName("debug")

    buildTypes {
        getByName("debug") { signingConfig = mibotSigning }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = mibotSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material:material-icons-extended")

    // 复用 Mibot core（vd/流云体/记忆）
    implementation(project(":core"))
}
