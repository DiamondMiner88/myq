plugins {
    kotlin("jvm") version "1.7.10"
    id("java")
}

dependencies {
    val ktorVersion = "2.0.3"

    implementation("io.ktor:ktor-client-core:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-java:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "11"
    }
    test {
        useJUnitPlatform()
    }
}