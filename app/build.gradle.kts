plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 5

android {
    namespace = "com.cleantv.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cleantv.launcher"
        minSdk = 24
        targetSdk = 34
        versionCode = releaseVersionCode
        versionName = "3.0.$releaseVersionCode"
    }

    // ระบุ signing key โดยตรง ไม่ใช้ debug signing config อัตโนมัติ
    // เพื่อรักษา certificate เดิมของแอปที่ติดตั้งอยู่
    signingConfigs {
        create("cleantvRelease") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("cleantvRelease")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
