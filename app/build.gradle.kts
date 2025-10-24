plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // KORRIGERING: Använd aliaset från din toml-fil
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.victorkoffed.projektandroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.projektandroid"
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

    compileOptions {
        // Behåller dina egenskaper exakt som du skickade
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // (Valfritt i framtiden: överväg Java 17 för snabbare builds/nyare API:er)
    }
    kotlin {
        // Behåller din toolchain
        jvmToolchain(11)
    }
    buildFeatures{
        compose = true
    }
}

dependencies {
    // --- Dina befintliga beroenden (alla korrekta) ---
    implementation(platform(libs.androidx.compose.bom))

    // Compose (BOM styr versionerna – lämna utan versionssuffix)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.runtime.livedata)

    // Ikoner (styrda av BOM)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Test för Compose
    androidTestImplementation(platform(libs.androidx.compose.bom))

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Övrigt
    implementation(libs.volley)
    implementation(libs.coil.compose)

    // --- TILLAGD FRÅN GUIDEN: Room Databas (via Version Catalog) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler) // Denna rad är nu korrekt

    // --- TILLAGD FÖR NAVIGATION ---
    implementation(libs.androidx.navigation.compose)

    // --- TILLAGD FÖR CAMERAX ---
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)

    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // --- SLUT CAMERAX ---
}