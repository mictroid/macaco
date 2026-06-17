import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.play.publisher)
}

val localProperties = Properties()
rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProperties.load(it) }

// Release signing is read from a git-ignored keystore.properties at the repo root. When it's
// absent (most dev machines / CI), release builds are simply left unsigned so debug builds and
// `assembleDebug` keep working. See docs/release-setup.md.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "com.houseofmmminq.macaco"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.houseofmmminq.macaco"
        minSdk = 24
        targetSdk = 36
        versionCode = 14
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY") ?: ""
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Bundle native debug symbols so Play can symbolicate native crashes/ANRs
            // (clears the "native code without debug symbols" upload warning).
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// Google Play publishing (Triple-T gradle-play-publisher). Locally, the credential is a Play
// Developer API service-account key kept in a git-ignored play-service-account.json at the repo
// root — see docs/release-setup.md for how to create it. In CI (GitHub Actions), that file isn't
// present; instead the workflow's Workload Identity Federation auth step writes a short-lived
// credentials file (impersonating play-publisher) and exports its path via
// GOOGLE_APPLICATION_CREDENTIALS — feed that file straight to serviceAccountCredentials rather
// than going through GPP's ADC auto-detection, which produced a 403 (unclear why; this is the
// pattern documented as working by other Play-publishing GitHub Actions).
play {
    val playServiceAccountFile = rootProject.file("play-service-account.json")
    val ciCredentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    if (playServiceAccountFile.exists()) {
        serviceAccountCredentials.set(playServiceAccountFile)
    } else if (ciCredentialsPath != null) {
        serviceAccountCredentials.set(file(ciCredentialsPath))
    }
    // Closed testing's default track is still called "alpha" in the Play Developer API/GPP,
    // even though Play Console itself just calls it "Closed testing" in the UI.
    track.set("alpha")
    defaultToAppBundles.set(true)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.play.services.auth)
    implementation(libs.revenuecat.purchases)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.api.client.android) { exclude(group = "org.apache.httpcomponents") }
    implementation(libs.google.api.services.drive) { exclude(group = "org.apache.httpcomponents") }
    implementation(libs.play.review.ktx)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
