import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform.module")
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
        bundledPlugin("org.jetbrains.plugins.clion.radler")

        testFramework(TestFrameworkType.Platform)
    }
    compileOnly(project(":shared"))
    testImplementation(project(":shared"))
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test> {
    systemProperty("patch.engine.backend.freeze.timeout", "0")
}
