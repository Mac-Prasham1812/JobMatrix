plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.jobmatrix.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jobmatrix.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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

}

dependencies {

    // ✅ Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))

    // ✅ Firebase libraries (NO versions)
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation(libs.androidx.appcompat)
    implementation("com.google.android.material:material:1.12.0")
    implementation ("com.google.android.material:material:1.11.0")
    implementation(libs.androidx.monitor)
    implementation(libs.androidx.junit.ktx)
    testImplementation(libs.junit.junit)
    implementation ("com.facebook.shimmer:shimmer:0.5.0")




}
