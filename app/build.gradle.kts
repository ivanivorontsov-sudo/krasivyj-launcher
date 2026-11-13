import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Signing: keystore.properties locally (not committed) or env vars in CI.
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = System.getenv(env) ?: ksProps.getProperty(key)

val appVersionCode = (System.getenv("VERSION_CODE") ?: "1470").toInt()
val appVersionName = System.getenv("VERSION_NAME") ?: "1.4.7"

android {
    namespace = "org.ivanivorontsov.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.ivanivorontsov.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        resourceConfigurations += listOf("ru", "en")
        // СуперСмарт: «Мудрость» ships ONNX Runtime native libs — phones only (+x86_64 for emulator tests).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            if (project.hasProperty("withX86")) abiFilters += "x86_64"
        }
    }

    signingConfigs {
        create("upload") {
            val store = signingValue("storeFile", "SIGNING_STORE_FILE")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    flavorDimensions += "dist"
    productFlavors {
        // Full APK (sideload): everything, incl. «Стоп-экран», screen lock, DNS filter, digit overlay.
        create("full") { dimension = "dist"; buildConfigField("boolean", "PLAY", "false"); buildConfigField("boolean", "HAS_MUDROST", "true"); buildConfigField("boolean", "HAS_MODULES", "true"); buildConfigField("boolean", "HAS_BOOK", "true") }
        // Google Play: same package, without Play-restricted parts (see src/play/AndroidManifest.xml).
        create("play") { dimension = "dist"; buildConfigField("boolean", "PLAY", "true"); buildConfigField("boolean", "HAS_MUDROST", "true"); buildConfigField("boolean", "HAS_MODULES", "true"); buildConfigField("boolean", "HAS_BOOK", "true") }
        // Sideload, installs next to the main app: all modules except «Мудрость», reader without the bundled book.
        create("nowisdom") {
            dimension = "dist"; versionNameSuffix = "-nowisdom"
            buildConfigField("boolean", "PLAY", "false"); buildConfigField("boolean", "HAS_MUDROST", "false"); buildConfigField("boolean", "HAS_MODULES", "true"); buildConfigField("boolean", "HAS_BOOK", "false")
        }
        // Sideload «Лёгкий»: only the launcher itself, no built-in modules, no «Мудрость», no book.
        create("light") {
            dimension = "dist"; versionNameSuffix = "-light"
            buildConfigField("boolean", "PLAY", "false"); buildConfigField("boolean", "HAS_MUDROST", "false"); buildConfigField("boolean", "HAS_MODULES", "false"); buildConfigField("boolean", "HAS_BOOK", "false")
        }
    }

    sourceSets {
        getByName("full") { assets.srcDir("src/withBook/assets") }
        getByName("play") { assets.srcDir("src/withBook/assets") }
    }

    buildTypes {
        release {
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // CI signs with the upload key. A local build without keystore.properties falls back
            // to the debug keystore so an installable APK can still be produced.
            signingConfig = if (signingValue("storeFile", "SIGNING_STORE_FILE") != null) signingConfigs.getByName("upload") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi", "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi", "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "DebugProbesKt.bin", "kotlin-tooling-metadata.json", "/kotlin/**")
        }
        jniLibs { useLegacyPackaging = true }
    }
    androidResources { noCompress += listOf("onnx", "bin") }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// CI must not publish a debug-signed release. Local builds may fall back to the debug keystore.
gradle.taskGraph.whenReady {
    if (System.getenv("CI") == "true" && allTasks.any { it.name.contains("Release") && (it.name.startsWith("assemble") || it.name.startsWith("package")) }) {
        require(signingValue("storeFile", "SIGNING_STORE_FILE") != null) {
            "Release signing key missing: provide keystore.properties or SIGNING_* env vars"
        }
    }
}

composeCompiler {
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("app/compose-stability.conf")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // СуперСмарт modules
    // «Мудрость» only in the main builds; other modules in all but «Лёгкий».
    for (cfg in listOf("fullImplementation", "playImplementation")) add(cfg, project(":modules:mudrost"))
    for (cfg in listOf("fullImplementation", "playImplementation", "nowisdomImplementation")) {
        add(cfg, project(":modules:kodeks")); add(cfg, project(":modules:boec")); add(cfg, project(":modules:lite"))
    }
    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    // «Стоп-экран» engine (ported from ScreenLimit) uses org.json: real implementation for JVM unit tests.
    testImplementation("org.json:json:20231013")
}
