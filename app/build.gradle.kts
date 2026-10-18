import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// La compilación automática pasa la versión más reciente del extractor.
// Si no se pasa nada, se usa esta.
val npeVersion = (project.findProperty("npeVersion") as String?)
    ?.takeIf { it.isNotBlank() && it != "null" } ?: "v0.24.8"

android {
    namespace = "uy.eco.musica"
    compileSdk = 35

    defaultConfig {
        applicationId = "uy.eco.musica"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0.${versionCode}"

        // Playlists compartidas (Firebase). Vacío = función desactivada.
        fun prop(name: String) = (project.findProperty(name) as String?).orEmpty()
            .filter { it.isLetterOrDigit() || it == '-' }
        buildConfigField("String", "FS_PROJECT", "\"${prop("fsProject")}\"")
        buildConfigField("String", "FS_GROUP", "\"${prop("fsGroup")}\"")
    }

    // Firma fija: así cada versión nueva se instala encima de la anterior.
    // La clave la descifra la compilación automática con el secreto ALFA_KS_PASS.
    val keystorePath = System.getenv("ALFA_KEYSTORE")
    val keystorePass = System.getenv("ALFA_KS_PASS")
    val hasKey = !keystorePath.isNullOrBlank() && !keystorePass.isNullOrBlank()

    signingConfigs {
        if (hasKey) {
            create("personal") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePass
                keyAlias = "alfa"
                keyPassword = keystorePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName(if (hasKey) "personal" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.4")

    // Búsqueda y obtención del audio de YouTube / YouTube Music
    implementation("com.github.TeamNewPipe:NewPipeExtractor:$npeVersion")

    // Reproductor y sesión multimedia (notificación, pantalla bloqueada, auriculares)
    val media3 = "1.6.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")

    // Interfaz
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
