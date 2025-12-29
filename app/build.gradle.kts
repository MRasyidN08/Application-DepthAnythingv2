plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.application" // Ganti jika perlu
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.depthnavapp"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }

        // --- PERBAIKAN: Argumen CMake ditempatkan di sini ---
        externalNativeBuild {
            cmake {
                //
                // !! PENTING: GANTI PATH DI BAWAH INI !!
                // Sesuaikan dengan lokasi folder OpenCV SDK di komputer Anda
                //
                arguments.add("-DOpenCV_DIR=D:/Skripsi/Asset/opencv-4.12.0-android-sdk/OpenCV-android-sdk/sdk/native/jni")

            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }


    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // --- TAMBAHAN PENTING ---
    // Pastikan nama file ini sama persis dengan file .aar yang Anda download
// (Ganti versi "0.3.0" jika ada yang lebih baru, tapi ini stabil)
    implementation("org.pytorch:pytorch_android_lite:1.13.1")
    implementation("org.pytorch:pytorch_android_torchvision_lite:1.13.1")

    // Dependensi Inti
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // --- DEPENDENSI CAMERAX ---
    val cameraXVersion = "1.3.4"
    implementation("androidx.camera:camera-core:${cameraXVersion}")
    implementation("androidx.camera:camera-camera2:${cameraXVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraXVersion}")
    implementation("androidx.camera:camera-view:${cameraXVersion}")

    // Dependensi Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}