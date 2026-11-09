plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ir.rokoi.rmho"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.rokoi.rmho"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "0.5.0"
    }

    // Permanent signing key (GitHub Secrets) so every build shares ONE
    // signature and updates install over each other. Without the env vars
    // (local dev), Gradle falls back to the default debug key.
    val ksPath = System.getenv("RMHO_KEYSTORE") ?: ""
    if (System.getenv("CI") == "true" && ksPath.isEmpty()) {
        throw GradleException("RMHO keystore env missing in CI - refusing to build an unsigned-by-us APK")
    }
    if (ksPath.isNotEmpty()) {
        signingConfigs {
            create("fixed") {
                storeFile = file(ksPath)
                storePassword = System.getenv("RMHO_KS_PASS")
                keyAlias = System.getenv("RMHO_KEY_ALIAS")
                keyPassword = System.getenv("RMHO_KEY_PASS")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            if (ksPath.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("fixed")
            }
        }
        release {
            if (ksPath.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("fixed")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
}
