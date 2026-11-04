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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // ใช้ signing key เดิมเพื่อให้อัปเดตทับ CleanTV ที่ติดตั้งอยู่ได้
            signingConfig = signingConfigs.getByName("debug")
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
