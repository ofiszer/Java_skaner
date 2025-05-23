plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.java_skaner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.java_skaner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.play.services.mlkit.document.scanner)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta3")
    implementation("com.rmtheis:tess-two:5.4.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
}