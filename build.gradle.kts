import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.kotlinx.kover") version "0.9.7"
}

group = "com.github.eviltak.taef"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        clion("2025.1")
        bundledPlugin("com.intellij.clion")
        bundledPlugin("com.intellij.cidr.base")
        bundledPlugin("com.intellij.cidr.lang")
        bundledPlugin("com.intellij.nativeDebug")
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.14.9") {
        // MockK's transitive kotlinx-coroutines conflicts with the version
        // bundled in the IntelliJ platform, causing test hangs/crashes
        exclude(group = "org.jetbrains.kotlinx")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "253.*"
        }
    }
    buildSearchableOptions = false
}
