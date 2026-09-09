plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.devzyden.stepcounterbyzyden"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.devzyden.stepcounterbyzyden"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // FIX applied here: added "=" for strict Kotlin DSL requirements
    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Core Platform Layouts
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Zyden's Core System Trackers (FIXED SYNTAX)
    implementation(libs.google.play.services.location)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Testing Frameworks
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    val workVersion = "2.9.1" // Use the latest stable version
    implementation("androidx.work:work-runtime-ktx:$workVersion")
}

