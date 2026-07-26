import java.io.FileInputStream
import java.util.Properties

// Read keys from secret.properties
val secretProperties = Properties()
val secretPropertiesFile = rootProject.file("secret.properties")
if (secretPropertiesFile.exists()) {
    secretProperties.load(FileInputStream(secretPropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

android {
    namespace = "com.yourteam.smartcare"
    compileSdk = 37 // CHANGED FROM 36 TO 37

    defaultConfig {
        applicationId = "com.yourteam.smartcare"
        minSdk = 24
        targetSdk = 36
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
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // Enables BuildConfig generation
    }
}

dependencies {

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Jetpack Navigation for Compose
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Supabase Dependencies
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.1"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:2.3.12")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    val ktorVersion = "2.3.12" // Or 3.0.0+ if using Supabase v3

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    implementation("io.ktor:ktor-client-plugins:$ktorVersion") // <--- Fixes HttpTimeout missing class
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-plugins:3.1.1")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.1")) // Use your current version
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt") // or gotrue-kt if on v2

    // 2. REQUIRED: Ktor Engine & Core matching Supabase Ktor version
    // If using Supabase 3.x:
    implementation("io.ktor:ktor-client-okhttp:3.1.1")
    implementation("io.ktor:ktor-client-core:3.1.1")
}
configurations.all {
    resolutionStrategy {
        // Enforce Ktor 3.1.1 across all dependencies
        force("io.ktor:ktor-client-core:3.1.1")
        force("io.ktor:ktor-client-okhttp:3.1.1")
        force("io.ktor:ktor-client-plugins:3.1.1")
    }
}