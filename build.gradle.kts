import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.0"
}

group = "io.github.thelimepixel.bento"

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
        }
    }

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
        testImplementation("org.jetbrains.kotlin:kotlin-test:1.8.20-RC")
    }

    tasks.test {
        useJUnitPlatform()
    }

    kotlin {
        jvmToolchain(20)
    }
}
