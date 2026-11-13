import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Chiave di firma vera: fuori dal repo, in ~/cantacard-keys/keystore.properties (oppure nel file
// indicato dalla variabile d'ambiente CANTACARD_SIGNING) con storeFile, storePassword, keyAlias e
// keyPassword; storeFile può essere relativo alla cartella del file. Senza, la release viene
// firmata con la chiave di debug (solo per provarla in locale, mai da distribuire).
val signingFile: File = providers.environmentVariable("CANTACARD_SIGNING").orNull?.let(::File)
    ?: File(System.getProperty("user.home"), "cantacard-keys/keystore.properties")
val signing: Properties? = signingFile.takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

android {
    namespace = "dev.cantacard"
    compileSdk = 37

    defaultConfig {
        // Definitivo (scelto dall'utente): dopo la pubblicazione non si può più cambiare.
        applicationId = "io.github.loller111.cantacard"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        if (signing != null) {
            create("release") {
                fun value(name: String) = signing.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw GradleException("Manca \"$name\" in $signingFile")
                storeFile = signingFile.parentFile.resolve(value("storeFile"))
                storePassword = value("storePassword")
                keyAlias = value("keyAlias")
                keyPassword = value("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug").also {
                    logger.warn("CantaCard: chiave di firma non trovata ($signingFile), release firmata con la chiave di debug")
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

    sourceSets {
        // Il catalogo negozi ha una sola sorgente, catalog/stores.json alla radice del repo:
        // finisce negli asset dell'APK ed è lo stesso file servito raw da GitHub.
        getByName("main") {
            assets.srcDir(rootProject.file("catalog"))
        }
    }

    androidResources {
        // Solo le lingue dell'interfaccia: elimina le traduzioni delle librerie.
        // Aggiungere qui ogni nuova lingua tradotta.
        localeFilters += listOf("it")
    }

    // Niente blocco dipendenze cifrato di Google nell'APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "/kotlin/**",
                "/kotlin-tooling-metadata.json",
                "/DebugProbesKt.bin",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
