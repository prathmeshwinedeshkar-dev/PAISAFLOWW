plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.paisafloww.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.paisafloww.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // Optional personal signing key. The key file is NEVER stored in the repository.
    // GitHub Actions writes it to app/personal.jks from a private Secret when you add one.
    // Without it, Gradle signs with the standard Android debug key, so anyone can build this project.
    val personalKeystore = file("personal.jks")
    signingConfigs {
        create("personal") {
            if (personalKeystore.exists()) {
                storeFile = personalKeystore
                storePassword = System.getenv("PAISA_KS_PASS")
                keyAlias = System.getenv("PAISA_KEY_ALIAS")
                keyPassword = System.getenv("PAISA_KEY_PASS")
            }
        }
    }

    buildTypes {
        debug {
            if (personalKeystore.exists()) signingConfig = signingConfigs.getByName("personal")
        }
        release {
            isMinifyEnabled = false
            if (personalKeystore.exists()) signingConfig = signingConfigs.getByName("personal")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    lint { abortOnError = false }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
