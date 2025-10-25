plugins {
    // Standardplugins för Android-applikationer
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // Kotlin-specifika plugins
    alias(libs.plugins.kotlin.compose)

    // Kotlin Symbol Processing (KSP) krävs för Room-databasens kompileringsprocess.
    alias(libs.plugins.ksp)
}

android {
    // Sätter applikationens unika namnrymden.
    namespace = "com.victorkoffed.projektandroid"
    // Kompilerar mot den senaste stabila Android-versionen.
    compileSdk = 36

    defaultConfig {
        // Applikationens unika ID.
        applicationId = "com.example.projektandroid"
        // Lägsta stödda Android-version (API 24, Android 7.0 Nougat).
        minSdk = 24
        // Mål-SDK, bör matcha compileSdk för bästa kompatibilitet.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Avaktiverar obfuscation för enklare felsökning i Release-builds (om det behövs).
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Sätter Java-versionen till 11 för käll- och målkompatibilitet.
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        // Konfigurerar Kotlin JVM Toolchain att använda Java 11.
        jvmToolchain(11)
    }
    buildFeatures{
        // Aktiverar Compose-stöd i modulen.
        compose = true
    }
}

dependencies {
    // --- COMPOSE-BEROENDEN ---

    // Använder Compose BOM (Bill of Materials) för att säkerställa att alla Compose-bibliotek
    // använder kompatibla versioner. Denna ska vara av typen 'platform'.
    implementation(platform(libs.androidx.compose.bom))

    // Kärnbiblioteken för UI, Material Design 3, layout (Foundation) och förhandsvisningar.
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    // Felsökningsverktyg för Compose (Debug-specifikt)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Compose-stöd för LiveData (för att konvertera LiveData till Compose State)
    implementation(libs.androidx.compose.runtime.livedata)

    // Ikoner från Material Icons (kärna och utökad uppsättning).
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    // Compose-specifik ViewModel-integration (t.ex. 'viewModel()' funktionen).
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose Test (BOM styr versionerna)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // UI-tester för Compose.
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Test-manifest (Debug-specifikt)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- ANDROIDX OCH KÄRNBEROENDEN ---

    // Standard AndroidX-kärnbibliotek (t.ex. Context, Resources).
    implementation(libs.androidx.core.ktx)
    // Lifecycle-stöd för Kotlin-coroutines.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Compose-integration för Activity (setContent).
    implementation(libs.androidx.activity.compose)

    // Testbibliotek (JUnit, AndroidX-tester och Espresso).
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- ÖVRIGA FUNKTIONELLA BERODEN ---

    // Volley för hantering av nätverksförfrågningar (REST API-kommunikation).
    implementation(libs.volley)
    // Coil för asynkron bildladdning i Compose-komponenter.
    implementation(libs.coil.compose)

    // --- ROOM DATABASBEROENDEN ---

    // Room Runtime: Kärnbiblioteket för databasen.
    implementation(libs.androidx.room.runtime)
    // Room KTX: Kotlin-extensioner för Coroutine-stöd i Room.
    implementation(libs.androidx.room.ktx)
    // Room Compiler: KSP-processorn som genererar databasimplementeringen vid kompilering.
    ksp(libs.androidx.room.compiler)

    // --- NAVIGATION ---

    // Compose Navigation: Hanterar navigering mellan skärmar i Compose-hierarkin.
    implementation(libs.androidx.navigation.compose)

    // --- CAMERAX FÖR BILDHANTERING ---

    // CameraX Core och Camera2-integration.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    // CameraX-integration med Android Lifecycles.
    implementation(libs.androidx.camera.lifecycle)
    // CameraX View-komponenter.
    implementation(libs.androidx.camera.view)
}
