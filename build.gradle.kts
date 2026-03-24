import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.kotlinx.kover") version "0.9.7" apply false
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
        val localIdePath = providers.gradleProperty("clion.localIde.path")
        if (localIdePath.isPresent) {
            local(localIdePath.get())
        } else {
            clion("2025.3")
        }

        bundledPlugin("com.intellij.clion")
        bundledPlugin("com.intellij.cmake")
        bundledPlugin("com.intellij.nativeDebug")
        bundledPlugin("org.jetbrains.plugins.clion.radler")

        pluginComposedModule(implementation(project(":shared")))
        pluginComposedModule(implementation(project(":classic")))
        pluginComposedModule(implementation(project(":nova")))

        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(project(":shared"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.14.9") {
        exclude(group = "org.jetbrains.kotlinx")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "253.*"
        }
    }
    buildSearchableOptions = false
    pluginVerification {
        ides {
            recommended()
        }
    }
}
