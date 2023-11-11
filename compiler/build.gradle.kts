plugins {
    kotlin("jvm") version "1.9.0"
    application
}

version = "0.1"
group = "io.github.thelimepixel"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
}

tasks.test {
    useJUnitPlatform()
}