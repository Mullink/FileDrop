plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.liquorbee.invoicescanner"
    // Android 13 (API 33) per requirement. minSdk kept lower for broader device compatibility -
    // costs nothing extra since every API-33-specific behavior below (scoped media permissions,
    // exported-component requirements, notification permission) is either handled explicitly or
    // deliberately avoided (see AndroidManifest.xml / SessionManager / capture code comments).
    compileSdk = 33

    defaultConfig {
        applicationId = "com.liquorbee.invoicescanner"
        minSdk = 26
        targetSdk = 33
        // Bump both on every build that gets pushed to the GitHub FileDrop repo - versionCode is
        // what UpdateChecker compares against version.txt there to detect an out-of-date install.
        versionCode = 20
        versionName = "2.9"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
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

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.7.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Networking - hits the existing HennyAdminOnline API exactly as documented, no server changes.
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // JWT stored encrypted at rest, per-user login (matches the web app's own auth model).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Local-only drawing templates (never synced to the backend, per the approved plan).
    implementation("androidx.room:room-runtime:2.5.2")
    implementation("androidx.room:room-ktx:2.5.2")
    ksp("androidx.room:room-compiler:2.5.2")

    implementation("androidx.recyclerview:recyclerview:1.3.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Zebra's official, MIT-licensed "Scanner SDK for Android" (com.zebra.scannercontrol) - real
    // USB SNAPI-mode barcode delivery via SDKHandler/IDcsSdkApiDelegate, not keyboard-wedge input.
    // AAR pulled directly from https://github.com/ZebraDevs/Scanner-SDK-for-Android (identified by
    // decompiling the already-working sibling "Liquor Bee POS" app's own bundled classes - same
    // SDK it already uses successfully for this exact scanner). See capture/ZebraScannerController.kt.
    implementation(files("libs/barcode_scanner_library_v2.6.29.0-release.aar"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
