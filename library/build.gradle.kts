plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.kevindupas.networkmetrics"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        version = "1.0.0"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/kevindupas/android-network-metrics-sdk")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
    publications {
        register<MavenPublication>("release") {
            groupId = "com.kevindupas"
            artifactId = "network-metrics-sdk"
            version = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Network Metrics SDK")
                description.set("Android SDK for continuous mobile network quality measurement — speed, packet loss, radio signal, GPS, social media latency.")
                url.set("https://github.com/kevindupas/android-network-metrics-sdk")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("kevindupas")
                        name.set("Kevin Dupas")
                        email.set("dupas.dev@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:github.com/kevindupas/android-network-metrics-sdk.git")
                    developerConnection.set("scm:git:ssh://github.com/kevindupas/android-network-metrics-sdk.git")
                    url.set("https://github.com/kevindupas/android-network-metrics-sdk/tree/main")
                }
            }
        }
    }
}
