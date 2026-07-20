plugins {
    alias(libs.plugins.android.library)
}

val occtRoot = providers.gradleProperty("occtRoot").orNull
val occtJni = providers.gradleProperty("occtJni").orNull
val occtAssets = providers.gradleProperty("occtAssets").orNull

android {
    namespace = "com.medinaparra.freecadandroid.cadcore"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DOCCT_ANDROID_ROOT=${occtRoot ?: ""}"
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            if (!occtJni.isNullOrBlank()) {
                jniLibs.srcDir(occtJni)
            }
            if (!occtAssets.isNullOrBlank()) {
                assets.srcDir(occtAssets)
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation(libs.junit)
}
