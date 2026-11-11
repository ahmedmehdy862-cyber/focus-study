import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing material lives outside the project so it is never committed or shared:
//   ~/.focusstudy/keystore.properties  (path, alias, passwords)
//   ~/.focusstudy/release.jks          (the key itself)
// Without that file the release build simply stays unsigned instead of failing.
val keystorePropertiesFile = File(System.getProperty("user.home"), ".focusstudy/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}
val hasReleaseKey = keystorePropertiesFile.isFile &&
    !keystoreProperties.getProperty("storeFile").isNullOrBlank()
val updateFeedUrl = providers.gradleProperty("focusStudyUpdateFeedUrl").orElse("").get()
val escapedUpdateFeedUrl = updateFeedUrl.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.focusstudy.poc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.focusstudy.poc"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.12.0"
        buildConfigField("String", "UPDATE_FEED_URL", "\"$escapedUpdateFeedUrl\"")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = File(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("com.batoulapps.adhan:adhan:1.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
