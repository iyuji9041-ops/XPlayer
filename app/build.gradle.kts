plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.Shyam.xplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.Shyam.xplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        // Sirf zaroori CPU architectures rakhne se APK size kam rehta hai
        ndk {
            abiFilters.clear()
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }

        // Naye flat vector icons ko purane Android versions par crash hone se bachata hai
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // VLC ka playback engine - isi se video actually chalti hai
    implementation("org.videolan.android:libvlc-all:3.5.1")

    // Folder/video list thumbnails ke liye
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // MKV files ke thumbnails ke liye (VLC khud thumbnail nahi deta)
    implementation("com.github.wseemann:FFmpegMediaMetadataRetriever-core:1.0.15")
    implementation("com.github.wseemann:FFmpegMediaMetadataRetriever-native-arm64-v8a:1.0.15")
    implementation("com.github.wseemann:FFmpegMediaMetadataRetriever-native-armeabi-v7a:1.0.15")

    // yt-dlp & FFmpeg Runtime Integration
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Player ke upar gesture aur seek overlay Jetpack Compose se bana hai
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
