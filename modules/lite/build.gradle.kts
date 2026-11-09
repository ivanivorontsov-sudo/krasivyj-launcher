plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")

}

android {
    namespace = "org.ivanivorontsov.supersmart.lite"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = false }

}

dependencies {
    implementation("androidx.core:core:1.15.0")
}
