import java.util.Properties

// Read keys safely from secret.properties
val secretProperties = Properties()
val secretPropertiesFile = rootProject.file("secret.properties")
if (secretPropertiesFile.exists()) {
    secretPropertiesFile.inputStream().use { stream ->
        secretProperties.load(stream)
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)

    // Google Services plugin
    id("com.google.gms.google-services") version "4.4.1"
}

android {
    namespace = "com.yourteam.smartcare"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yourteam.smartcare"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject keys into BuildConfig
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${secretProperties.getProperty("SUPABASE_URL") ?: ""}\""
        )

        buildConfigField(
            "String",
            "SUPABASE_KEY",
            "\"${secretProperties.getProperty("SUPABASE_KEY") ?: ""}\""
        )

        buildConfigField(
            "String",
            "GEOAPIFY_API_KEY",
            "\"${secretProperties.getProperty("GEOAPIFY_API_KEY") ?: ""}\""
        )
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

    // Java 8+ API desugaring
    // Required because minSdk is 24 while the app uses java.time APIs.
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        )
    }
}

dependencies {

    // Java 8+ API desugaring
    // Allows java.time APIs to work on devices with API 24+
    coreLibraryDesugaring(
        "com.android.tools:desugar_jdk_libs:2.1.5"
    )

    // AndroidX Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Material Icons
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation for Compose
    implementation(libs.androidx.navigation.compose)

    // KotlinX Serialization JSON
    implementation(libs.kotlinx.serialization.json)

    // Supabase Dependencies
    implementation(
        platform(
            "io.github.jan-tennert.supabase:bom:3.1.1"
        )
    )
    implementation(
        "io.github.jan-tennert.supabase:postgrest-kt"
    )
    implementation(
        "io.github.jan-tennert.supabase:auth-kt"
    )
    implementation(
        "io.github.jan-tennert.supabase:functions-kt"
    )

    // Ktor Dependencies
    val ktorVersion = "3.1.1"

    implementation(
        "io.ktor:ktor-client-core:$ktorVersion"
    )
    implementation(
        "io.ktor:ktor-client-okhttp:$ktorVersion"
    )
    implementation(
        "io.ktor:ktor-client-content-negotiation:$ktorVersion"
    )
    implementation(
        "io.ktor:ktor-serialization-kotlinx-json:$ktorVersion"
    )

    // Testing
    testImplementation(libs.junit)

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )
    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )
    androidTestImplementation(
        libs.androidx.espresso.core
    )
    androidTestImplementation(
        libs.androidx.junit
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )
    debugImplementation(
        libs.androidx.compose.ui.tooling
    )

    // Firebase
    implementation(
        "com.google.firebase:firebase-messaging-ktx:23.4.1"
    )
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3"
    )
    implementation(
        "com.google.auth:google-auth-library-oauth2-http:1.20.0"
    )

    // Emergency & Notifications
    implementation(
        "androidx.work:work-runtime-ktx:2.9.0"
    )
    implementation(
        "com.google.android.gms:play-services-location:21.3.0"
    )

    // AI Scanner
    val cameraxVersion = "1.3.4"
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.30.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation(
        "androidx.camera:camera-core:$cameraxVersion"
    )
    implementation(
        "androidx.camera:camera-camera2:$cameraxVersion"
    )
    implementation(
        "androidx.camera:camera-lifecycle:$cameraxVersion"
    )
    implementation(
        "androidx.camera:camera-view:$cameraxVersion"
    )

    implementation(
        "com.squareup.okhttp3:okhttp:4.12.0"
    )

    implementation(
        "com.squareup.retrofit2:retrofit:2.9.0"
    )
    implementation(
        "com.squareup.retrofit2:converter-gson:2.9.0"
    )

    implementation(
        "com.google.code.gson:gson:2.10.1"
    )
}

configurations.all {
    resolutionStrategy {
        force(
            "io.ktor:ktor-client-core:3.1.1"
        )
        force(
            "io.ktor:ktor-client-okhttp:3.1.1"
        )
    }
}