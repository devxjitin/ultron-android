import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("io.objectbox")
}

// Optional compile-time default API key, read from local.properties (gitignored, never
// committed). The app also lets the key be entered/changed at runtime from Settings, which
// takes priority -- this is just a convenience fallback for development.
// Add: GEMINI_API_KEY=your_key_here  to android/local.properties
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}
val geminiApiKey: String = localProperties.getProperty("GEMINI_API_KEY", "")
// Public client id of the GitHub OAuth App used for "Sign in with GitHub" (device flow, no secret).
// Add: GITHUB_CLIENT_ID=Iv1.xxxx  to android/local.properties. Blank = the connector asks for a token instead.
val githubClientId: String = localProperties.getProperty("GITHUB_CLIENT_ID", "")
// Private URL (with its access token) of the user's YouTube MCP server, so the Marketplace can add it in one tap.
// Add: YOUTUBE_MCP_URL=https://...  to android/local.properties. Blank = the connector asks the user to paste it.
val youtubeMcpUrl: String = localProperties.getProperty("YOUTUBE_MCP_URL", "")
// Base URL (with its access token) of the Google Workspace MCP server on AWS that the Google connectors talk to.
// Add: GOOGLE_MCP_URL=https://...  to android/local.properties.
val googleMcpUrl: String = localProperties.getProperty("GOOGLE_MCP_URL", "")
// Azure OpenAI resource used by the "Azure API key" chat option (the key itself is entered in-app).
// Add: AZURE_ENDPOINT=https://<resource>.openai.azure.com  to android/local.properties.
val azureEndpoint: String = localProperties.getProperty("AZURE_ENDPOINT", "")
// URL of the update server on AWS (with its access token), used by the in-app "update from AWS" feature.
// Add: UPDATE_URL=https://.../<token>/latest  to android/local.properties. scripts/publish-update.ps1 uploads new builds.
val updateUrl: String = localProperties.getProperty("UPDATE_URL", "")
// Every build gets a higher version code than the one before (minutes since 1970), so the phone can always tell
// which build is newer. scripts/publish-update.ps1 passes the same number with -PappVersionCode.
val appVersionCode: Int = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: (System.currentTimeMillis() / 60000).toInt()
val appVersionName: String = (project.findProperty("appVersionName") as String?) ?: "1.0.0"

// Release signing config, also read from local.properties (gitignored) so the keystore
// path and passwords never end up in source control. Add these to android/local.properties:
// RELEASE_STORE_FILE=keystore/nivi-release.jks
// RELEASE_STORE_PASSWORD=...
// RELEASE_KEY_ALIAS=nivi-release
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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.assistant.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$githubClientId\"")
        buildConfigField("String", "YOUTUBE_MCP_URL", "\"$youtubeMcpUrl\"")
        buildConfigField("String", "GOOGLE_MCP_URL", "\"$googleMcpUrl\"")
        buildConfigField("String", "AZURE_ENDPOINT", "\"$azureEndpoint\"")
        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
    }

    signingConfigs {
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Classic View-based Material Components theme resources -- needed because the
    // manifest's launch theme (themes.xml) parents off Theme.Material3.*, which lives here,
    // not in the Compose Material3 artifact above.
    implementation("com.google.android.material:material:1.12.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.security:security-crypto:1.0.0")
    // Google's own sign-in/consent screen for the Google Workspace connectors (Calendar, Gmail, Drive...).
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
