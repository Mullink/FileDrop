plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    // KSP, not kapt: kapt's javac-based stub generation doesn't work under JDK 17+'s module
    // system (IllegalAccessError against jdk.compiler's internals) - KSP avoids that whole
    // problem, and is Google's own recommended replacement for annotation processing anyway.
    id("com.google.devtools.ksp") version "1.9.10-1.0.13" apply false
}
