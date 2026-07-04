plugins {
	kotlin("jvm") version "2.2.21" apply false
	kotlin("plugin.spring") version "2.2.21" apply false
	id("org.springframework.boot") version "4.0.5" apply false
	id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "com.architecturelab"
version = "0.0.1-SNAPSHOT"

repositories {
	mavenCentral()
}

subprojects {
	group = rootProject.group
	version = rootProject.version

	repositories {
		mavenCentral()
	}
}
