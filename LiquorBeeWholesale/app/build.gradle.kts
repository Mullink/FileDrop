plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.liquorbee.wholesale"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.liquorbee.wholesale"
        minSdk = 26
        targetSdk = 33
        // Bump both on every build that gets pushed to the GitHub FileDrop repo - versionCode is
        // what UpdateChecker compares against version.txt there to detect an out-of-date install.
        versionCode = 7
        versionName = "1.6"
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
    implementation("androidx.fragment:fragment-ktx:1.6.1")
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

    implementation("androidx.recyclerview:recyclerview:1.3.1")

    // Renders the animated Wholesale_OrderPlaced celebration GIF (a Giphy URL, DB-driven - see
    // dbo.Settings) - plain ImageView doesn't animate GIFs, Glide does out of the box.
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
