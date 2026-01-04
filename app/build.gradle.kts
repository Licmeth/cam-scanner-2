plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val composeVersion = "1.10.0"

android {
    namespace = "com.licmeth.camscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.licmeth.camscanner"
        minSdk = 24
        targetSdk = 35
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = true  //todo to be removed once fully migrated to Compose
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = composeVersion
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    //implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // Jetpack Compose
    implementation("androidx.activity:activity-compose:1.12.2")
    //implementation("androidx.compose.foundation:foundation:$composeVersion")
    implementation("androidx.compose.material:material:$composeVersion")
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling:$composeVersion")
    
    // CameraX
    implementation("androidx.camera:camera-core:1.5.2")
    implementation("androidx.camera:camera-camera2:1.5.2")
    implementation("androidx.camera:camera-lifecycle:1.5.2")
    implementation("androidx.camera:camera-view:1.5.2")
    
    // OpenCV
    implementation("org.opencv:opencv:4.12.0")
    
    // PDF generation
    implementation("com.itextpdf:itext7-core:7.2.5")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Data Store
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$composeVersion")
}
