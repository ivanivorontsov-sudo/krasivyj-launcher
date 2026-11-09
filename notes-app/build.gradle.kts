import java.util.Properties

plugins {
    id("com.android.application")
}

val ksProps = Properties().apply {
    val f = rootProject.file("notes-keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = System.getenv(env) ?: ksProps.getProperty(key)

val appVersionCode = (System.getenv("NOTES_VERSION_CODE") ?: "2").toInt()
val appVersionName = System.getenv("NOTES_VERSION_NAME") ?: "1.1.0"

android {
    namespace = "ru.zametki.notes"
    compileSdk = 36
    defaultConfig {
        applicationId = "ru.zametki.notes"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        resourceConfigurations += listOf("ru", "en")
    }
    sourceSets {
        getByName("main") {
            java.srcDir("../modules/lite/src/main/java/ru/zametki/notes")
        }
    }
    signingConfigs {
        create("upload") {
            val store = signingValue("storeFile", "NOTES_STORE_FILE")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = signingValue("storePassword", "NOTES_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "NOTES_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "NOTES_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (signingValue("storeFile", "NOTES_STORE_FILE") != null)
                signingConfigs.getByName("upload") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
}
