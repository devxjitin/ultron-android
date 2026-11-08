import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("io.objectbox")
}

// Firebase Auth, App Check and Crashlytics need android/app/google-services.json (gitignored). Without it the app
// still builds, but it cannot sign in to the Ultron API.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// Build-time values come from local.properties (gitignored, never committed). No Gemini or other API key is read
// here on purpose: anything compiled into the APK can be pulled out of it. Gemini always uses the user's own key
// entered in Developer settings.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

// Base URL of the Ultron API on AWS (checks the user's Firebase token, so it is not a secret). Overridable from local.properties.
val apiBaseUrl: String = localProperties.getProperty("API_BASE_URL", "https://api.devxjitin.dev")
// Website store where activation keys are sold; the activation screen links to it. Blank hides the link.
val storeUrl: String = localProperties.getProperty("STORE_URL", "")
// Every build gets a higher version code than the one before (minutes since 1970), so the phone can always tell
// which build is newer. scripts/publish-update.ps1 passes the same number with -PappVersionCode.
val appVersionCode: Int = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: (System.currentTimeMillis() / 60000).toInt()
// The version name is the newest "## x.y.z" heading in src/main/assets/changelog.md, so the app, the About page and the
// update notes can never disagree. -PappVersionName still overrides it for one-off builds.
val appVersionName: String = (project.findProperty("appVersionName") as String?)
    ?: file("src/main/assets/changelog.md").takeIf { it.exists() }?.useLines { lines ->
        lines.firstNotNullOfOrNull { Regex("""^##\s+v?(\d+\.\d+\.\d+)""").find(it)?.groupValues?.get(1) }
    }
    ?: "1.0.0"

// Release signing config, also read from local.properties (gitignored) so the keystore
// path and passwords never end up in source control. Add these to android/local.properties:
// RELEASE_STORE_FILE=keystore/release.jks
// RELEASE_STORE_PASSWORD=...
// RELEASE_KEY_ALIAS=release
// RELEASE_KEY_PASSWORD=...
val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE", "")
val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
val hasReleaseSigningConfig = releaseStoreFile.isNotBlank() &&
    releaseStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank()

android {
    namespace = "com.assistant.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.assistant.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "STORE_URL", "\"$storeUrl\"")
    }

    signingConfigs {
        // The phone's installed app is signed with this one key. A different key makes Android refuse every update
        // ("signatures do not match"), so debug and OTA builds always use it instead of whatever key the machine
        // would pick by default (~/.android/debug.keystore). Never regenerate or replace this file.
        val ultronKey = File(System.getProperty("user.home"), ".ultron-android/debug.keystore")
        if (ultronKey.exists()) {
            getByName("debug") {
                storeFile = ultronKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // Build published to the AWS update server: signed with the same debug key as the installed app
        // (so Android accepts it as an update), but shrunk and phone-ABI only, so the download is a fraction
        // of the debug APK's size and the app runs without debuggable slowdowns.
        create("ota") {
            initWith(getByName("debug"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "debug"
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }

    // These JVM tests exercise pure classifiers; Android logging is a no-op.
    testOptions { unitTests.isReturnDefaultValues = true }

    // local.properties is written by Android Studio (with the SDK path) and never ships; newer lint flags its backslashes.
    lint { disable += "PropertyEscape" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Real org.json (the Android one is only a stub in unit tests) and a local HTTP server for streaming tests.
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    // Classic View-based Material Components theme resources -- needed because the
    // manifest's launch theme (themes.xml) parents off Theme.Material3.*, which lives here,
    // not in the Compose Material3 artifact above.
    implementation("com.google.android.material:material:1.14.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.security:security-crypto:1.1.0")
    // Installs baseline-prof.txt on the phone: without Play Store nothing else compiles the profile ahead of time.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    // Identity (anonymous at first), proof that requests come from the genuine app, and crash and usage reporting.
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
