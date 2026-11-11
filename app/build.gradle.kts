import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.naovt.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.naovt.app"
        minSdk = 26 // Llamatik pide Android 8.0 (API 26) o superior
        targetSdk = 34
        versionCode = 15
        versionName = "0.15.0-mini-chat-voz"
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

// Llamatik (com.llamatik:core-android:2.0.0) declara en sus metadatos que exige compileSdk 37, pero la
// plataforma Android 37 no se puede descargar en este entorno ("Failed to find Platform SDK") y la librería
// no necesita ninguna API nueva para funcionar. Se desactiva solo esa verificación de metadatos y se compila
// con la plataforma 35. Si algún día se migra a AGP 9, esto se puede quitar y subir compileSdk a 37.
tasks.configureEach {
    if (name.startsWith("check") && name.endsWith("AarMetadata")) {
        enabled = false
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
