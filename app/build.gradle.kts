plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.vipcall.ringer"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.vipcall.ringer"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// بدون هیچ کتابخانه‌ی جانبی؛ فقط API خود اندروید
dependencies {
}
