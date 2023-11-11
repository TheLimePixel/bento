plugins {
    kotlin("jvm") version "1.9.0"
}


group = "io.github.thelimepixel"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.8.20-RC")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}