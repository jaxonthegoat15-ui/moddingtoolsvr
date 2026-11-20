plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.questmodder.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.questmodder.app"
        minSdk = 29
        targetSdk = 32
        versionCode = 3
        versionName = "0.3.0"
}
}
dependencies {
    implementation("com.android.tools.build:apksig:8.7.3")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}
