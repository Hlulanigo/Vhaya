import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val releaseKeystorePath = providers.environmentVariable("VHAYA_RELEASE_KEYSTORE_PATH")
    .orNull
    ?.takeIf { it.isNotBlank() }
val releaseKeystorePassword = providers.environmentVariable("VHAYA_RELEASE_KEYSTORE_PASSWORD")
    .orNull
    ?.takeIf { it.isNotBlank() }
val releaseKeyAlias = providers.environmentVariable("VHAYA_RELEASE_KEY_ALIAS")
    .orNull
    ?.takeIf { it.isNotBlank() }
val releaseKeyPassword = providers.environmentVariable("VHAYA_RELEASE_KEY_PASSWORD")
    .orNull
    ?.takeIf { it.isNotBlank() }
val releaseKeystoreFile = releaseKeystorePath?.let { file(it) }
val missingReleaseSigningValues = buildList {
    if (releaseKeystoreFile == null || !releaseKeystoreFile.isFile) {
        add("VHAYA_RELEASE_KEYSTORE_PATH (keystore file unavailable)")
    }
    if (releaseKeystorePassword == null) add("VHAYA_RELEASE_KEYSTORE_PASSWORD")
    if (releaseKeyAlias == null) add("VHAYA_RELEASE_KEY_ALIAS")
    if (releaseKeyPassword == null) add("VHAYA_RELEASE_KEY_PASSWORD")
}
val releaseSigningConfigured = missingReleaseSigningValues.isEmpty()

android {
    namespace = "com.wayfare.ride"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wayfare.ride"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        val dotEnvFile = rootProject.file(".env")
        val dotEnvMap = if (dotEnvFile.exists()) {
            dotEnvFile.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
                .associate { line ->
                    val idx = line.indexOf('=')
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                    key to value
                }
        } else {
            emptyMap()
        }

        val apiBaseUrl = (project.findProperty("VHAYA_API_BASE_URL") as? String)
            ?: System.getenv("VHAYA_API_BASE_URL")
            ?: dotEnvMap["VHAYA_API_BASE_URL"]
            ?: ""
        val firebaseWebApiKey = (project.findProperty("FIREBASE_WEB_API_KEY") as? String)
            ?: System.getenv("FIREBASE_WEB_API_KEY")
            ?: dotEnvMap["FIREBASE_WEB_API_KEY"]
            ?: ""
        buildConfigField(
            "String",
            "VHAYA_API_BASE_URL",
            "\"${apiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
        buildConfigField(
            "String",
            "FIREBASE_WEB_API_KEY",
            "\"${firebaseWebApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
        buildConfigField("boolean", "ALLOW_MOCK_PAYMENTS", "false")
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "ALLOW_MOCK_PAYMENTS", "true")
        }
        release {
            signingConfig = if (releaseSigningConfigured) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            isMinifyEnabled = true
            buildConfigField("boolean", "ALLOW_MOCK_PAYMENTS", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

gradle.taskGraph.whenReady { taskGraph ->
    val releaseArtifactTasks = taskGraph.allTasks.filter { task ->
        task.path.startsWith(":app:") &&
            task.name in setOf(
                "assembleRelease",
                "bundleRelease",
                "packageRelease",
                "packageReleaseBundle",
                "signReleaseBundle",
                "signReleaseApk"
            )
    }
    if (releaseArtifactTasks.isNotEmpty() && !releaseSigningConfigured) {
        throw GradleException(
            "Release packaging is blocked until a dedicated Vhaya release keystore is configured. " +
                "Missing or unavailable: ${missingReleaseSigningValues.joinToString()}. " +
                "Keep the keystore and passwords out of source control."
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
}
