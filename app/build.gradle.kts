plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    //noinspection NewerVersionAvailable
    id("com.apollographql.apollo3") version "3.7.0"
    alias(libs.plugins.google.services)

}

android {
    namespace = "com.azamovme.sozotvlogin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.azamovme.sozotvlogin"
        minSdk = 24
        targetSdk = 36
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}
apollo {
    packageName.set("com.azamovme.sozotvlogin")
    generateKotlinModels.set(true)
    excludes.add("**/schema.json.graphql")
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)

    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.fragment.ktx)

    implementation(libs.koin.android)

    implementation(libs.apollo.runtime)
    implementation(libs.okhttp)

    implementation(libs.datastore.preferences)

    implementation(libs.androidx.browser)
    implementation(libs.coil)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)

}
