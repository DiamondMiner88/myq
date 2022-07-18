group = "com.github.diamondminer88"
version = "1.0.0"

subprojects {
	repositories {
		mavenCentral()
	}
}

task<Delete>("clean") {
	delete(rootProject.buildDir)
}
