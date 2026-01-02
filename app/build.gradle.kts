plugins {
    id("com.android.application")
}


android {
    namespace = "com.sotiris.engine"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sotiris.engine"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
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

    buildFeatures {
        prefab = true
    }
}

// LibGDX versions
val gdxVersion = "1.12.1"
val box2DLightsVersion = "1.5"

dependencies {
    // Standard Android dependencies
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // LibGDX dependencies
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-box2d:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-ai:1.8.2")
    implementation("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-armeabi-v7a")
    implementation("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-arm64-v8a")
    implementation("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-x86")
    implementation("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-x86_64")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")

    // SFX library
    implementation("games.spooky.gdx:gdx-sfx:3.0.0")
    implementation("games.spooky.gdx:gdx-sfx-android:3.0.0")

    // VFX library
    implementation("com.crashinvaders.vfx:gdx-vfx-core:0.5.4")
    implementation("com.crashinvaders.vfx:gdx-vfx-effects:0.5.4")

    // PieMenu library
    implementation("com.github.payne911:PieMenu:5.0.0")

    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}