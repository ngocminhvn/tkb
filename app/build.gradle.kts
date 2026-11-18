plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sv.tnu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tkb.sv"
        ndk {
            // Ship native Rust implementations for common physical devices and emulators.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
        minSdk = 24
        targetSdk = 35
        // CI overwrites both with sed (versionCode = 100000 + run number, versionName = 1.0.<run number>)
        versionCode = 100000
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    // Text-based PDFs only; no OCR, PDFium or model assets bundled.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}

val rustAndroidAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
val verifyRustNativeLibraries = tasks.register("verifyRustNativeLibraries") {
    group = "verification"
    description = "Fail Android builds missing the native Rust schedule engine"
    doLast {
        val missing = rustAndroidAbis.filterNot { abi ->
            file("src/main/jniLibs/$abi/libtkb_core.so").isFile
        }
        check(missing.isEmpty()) {
            "Rust native libraries missing for: ${missing.joinToString()}. " +
                "Run ./scripts/build-rust-android.sh before building the Android APK."
        }
    }
}

tasks.named("preBuild") {
    dependsOn(verifyRustNativeLibraries)
}
