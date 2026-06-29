plugins {
	kotlin("jvm")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

dependencies {
	implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.5"))
	implementation("org.springframework:spring-context")
	implementation("org.springframework:spring-webflux")
	implementation("org.slf4j:slf4j-api")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
