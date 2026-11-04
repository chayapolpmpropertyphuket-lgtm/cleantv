plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 5

android {
    namespace = "com.cleantv.launcher"
    compileSdk = 34
    defaultConfig {
        // แพ็กเกจแยกเพื่อให้ติดตั้งทดสอบคู่กับ CleanTV เดิมได้
        // ห้ามเปลี่ยนกลับเป็น com.cleantv.launcher จนกว่าจะมีแผนย้ายแอปที่ผ่านการทดสอบ
        applicationId = "com.cleantv.launcher.preview"
        minSdk = 24
        targetSdk = 34
        versionCode = releaseVersionCode
        versionName = "3.0.$releaseVersionCode"
    }
    signingConfigs {
        create("cleantvRelease") {
            storeFile = rootProject.file("keystore/cleantv-release.jks")
            storePassword = providers.environmentVariable("CLEANTV_STORE_PASSWORD").orNull ?: ""
            keyAlias = providers.environmentVariable("CLEANTV_KEY_ALIAS").orNull ?: ""
            keyPassword = providers.environmentVariable("CLEANTV_KEY_PASSWORD").orNull ?: ""
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
