plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id ("kotlin-kapt")
}

android {
    namespace = "com.example.jigglevoiceassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.jigglevoiceassistant"
        minSdk = 24
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures{
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")


    implementation ("androidx.legacy:legacy-support-v4:1.0.0")

    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.5.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")

    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.recyclerview:recyclerview-selection:1.2.0-alpha01")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.test:monitor:1.4.0")

    implementation("androidx.room:room-runtime:2.4.2")
    kapt ("androidx.room:room-compiler:2.4.2")

    implementation("androidx.room:room-ktx:2.4.2")
    testImplementation("androidx.room:room-testing:2.4.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.3.7")

    implementation("com.google.android.gms:play-services-mlkit-text-recognition:18.0.0")

//    implementation("com.github.shubham0204:Text2Summary-Android:alpha-05")

//    implementation("com.theartofdev.edmodo:android-image-cropper:2.8.0")

//    implementation("com.github.KwabenBerko:OpenWeatherMap-Android-Library:2.1.0")

//    implementation("the.bot.box:horoscope-api:1.0.2")


}
