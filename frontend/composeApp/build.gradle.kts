plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "pkwmmap"
    compileSdk = 37

    defaultConfig {
        applicationId = "pkwmmap.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            kotlin.directories.addAll(listOf("src/commonMain/kotlin", "src/androidMain/kotlin"))
            res.directories.addAll(listOf("src/androidMain/res", "src/commonMain/composeResources"))
        }
        getByName("test") {
            kotlin.directories.add("src/commonTest/kotlin")
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(
        files(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/commonTest/kotlin"
        )
    )
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui:1.10.5")
    implementation("androidx.compose.ui:ui-graphics:1.10.5")
    implementation("androidx.compose.foundation:foundation:1.10.5")
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    detektPlugins("io.nlopez.compose.rules:detekt:0.6.8")
    ktlintRuleset("io.nlopez.compose.rules:ktlint:0.6.8")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
}
