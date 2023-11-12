plugins {
    kotlin("jvm") version "1.9.0"
}

group = "io.github.thelimepixel"

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
        testImplementation("org.jetbrains.kotlin:kotlin-test:1.8.20-RC")
    }

    tasks.test {
        useJUnitPlatform()
    }

    kotlin {
        jvmToolchain(8)
    }
}
