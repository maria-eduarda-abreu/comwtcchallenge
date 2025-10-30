/*
 * ATENÇÃO: Substitua o conteúdo do seu build.gradle.kts (Module :app)
 * por este. Você precisará sincronizar o projeto (Sync Now).
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Adicione o plugin do Google Services
    id("com.google.gms.google-services")
}

android {
    namespace = "com.wtc.challenge" // Certifique-se que é o seu pacote
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wtc.challenge"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.10.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

    // Firebase (BOM - Bill of Materials)
    // Isso garante que todas as bibliotecas do Firebase são compatíveis
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))

    // 1. Autenticação
    implementation("com.google.firebase:firebase-auth-ktx")

    // 2. Banco de Dados (Firestore) e 4. CRM
    implementation("com.google.firebase:firebase-firestore-ktx")

    // 3. Notificações
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Coroutines para chamadas assíncronas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")


    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}