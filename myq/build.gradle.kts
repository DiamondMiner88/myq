plugins {
	id("java")
	id("maven-publish")
	kotlin("jvm") version "1.6.21"
	kotlin("plugin.serialization") version "1.6.21"
}

group = "com.github.diamondminer88"
version = "1.0.0"

repositories {
	mavenCentral()
}

dependencies {
	val ktorVersion = "2.0.3"

	implementation("io.ktor:ktor-client-core:$ktorVersion")
	implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
	implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

	testImplementation(kotlin("test"))
	testImplementation("io.ktor:ktor-client-java:$ktorVersion")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}

java {
	sourceCompatibility = JavaVersion.VERSION_11
	targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
	explicitApi()
}

tasks {
	compileKotlin {
		kotlinOptions.jvmTarget = "11"
	}
	test {
		useJUnitPlatform()
	}
}

publishing {
	publications {
		register(project.name, MavenPublication::class) {
			from(components["java"])
			artifact(tasks["kotlinSourcesJar"])
		}
	}
}
