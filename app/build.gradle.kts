plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.joaohouto.clusterlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.joaohouto.clusterlauncher"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }



    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)

    // DataStore Preferences para persistência da Quick Dock
    implementation(libs.androidx.datastore.preferences)

    // Coil para carregamento de capas de mídia e ícones
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

project.afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") }.configureEach {
        doLast {
            val isRelease = name.contains("Release", ignoreCase = true)
            val subfolder = if (isRelease) "release" else "debug"
            val apkDir = layout.buildDirectory.dir("outputs/apk/$subfolder").orNull?.asFile ?: return@doLast
            val defaultApk = File(apkDir, if (isRelease) "app-release.apk" else "app-debug.apk")
            val targetApk = File(apkDir, if (isRelease) "ClusterLauncher-v${android.defaultConfig.versionName}.apk" else "ClusterLauncher-v${android.defaultConfig.versionName}-debug.apk")
            if (defaultApk.exists()) {
                defaultApk.copyTo(targetApk, overwrite = true)
                println("APK generated: ${targetApk.name}")
            }
        }
    }
}