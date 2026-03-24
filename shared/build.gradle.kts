import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform.module")
    id("org.jetbrains.kotlinx.kover")
}

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
        val localIdePath = providers.gradleProperty("clion.localIde.path")
        if (localIdePath.isPresent) {
            local(localIdePath.get())
        } else {
            clion("2025.3")
        }

        bundledPlugin("com.intellij.clion")
        bundledPlugin("com.intellij.cmake")
        bundledPlugin("com.intellij.nativeDebug")

        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.14.9") {
        exclude(group = "org.jetbrains.kotlinx")
    }
}
