import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.naovt.app"
    // 37: lo exige Llamatik (com.llamatik:core-android:2.0.0). targetSdk sigue en 34: el comportamiento
    // de la app no cambia, compileSdk solo decide contra qué versión de la API se compila.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.naovt.app"
        minSdk = 26 // Llamatik pide Android 8.0 (API 26) o superior
        targetSdk = 34
        versionCode = 14
        versionName = "0.14.0-ia-local"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Precaución: permite que las librerías usen APIs modernas de Java (java.time, etc.).
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // Los modelos de IA no se comprimen dentro del APK (no se achican y tardaría mucho).
        noCompress += "gguf"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("io.coil-kt:coil-compose:2.7.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // IA local (llama.cpp para Kotlin). Desde la versión 2.0 el artefacto se llama "core".
    implementation("com.llamatik:core:2.0.0")
}
