plugins {
	java
	idea
	id("org.springframework.boot") version "4.0.5"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.nn"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	// TODO handle later
	//implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-liquibase")

	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
	implementation("io.micrometer:micrometer-registry-prometheus")

	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")

	annotationProcessor("org.projectlombok:lombok")
	compileOnly("org.projectlombok:lombok")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-kafka")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

sourceSets {
	create("testUtils") {
		java.srcDir("src/test/utils/java")
		resources.srcDir("src/test/utils/resources")
		compileClasspath += sourceSets.main.get().output
		runtimeClasspath += output + compileClasspath
	}
	create("unitTest") {
		java.srcDir("src/test/unit/tests")
		resources.srcDir("src/test/unit/resources")
		compileClasspath += sourceSets.main.get().output + sourceSets["testUtils"].output
		runtimeClasspath += output + compileClasspath
	}
	create("integrationTest") {
		java.srcDir("src/test/integration/tests")
		resources.srcDir("src/test/integration/resources")
		compileClasspath += sourceSets.main.get().output + sourceSets["testUtils"].output
		runtimeClasspath += output + compileClasspath
	}
}

configurations {
	named("unitTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
	named("unitTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
	named("unitTestCompileOnly") { extendsFrom(configurations.testCompileOnly.get()) }
	named("unitTestAnnotationProcessor") { extendsFrom(configurations.testAnnotationProcessor.get()) }

	named("integrationTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
	named("integrationTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
	named("integrationTestCompileOnly") { extendsFrom(configurations.testCompileOnly.get()) }
	named("integrationTestAnnotationProcessor") { extendsFrom(configurations.testAnnotationProcessor.get()) }

	named("testUtilsImplementation") { extendsFrom(configurations.testImplementation.get()) }
	named("testUtilsRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
	named("testUtilsCompileOnly") { extendsFrom(configurations.testCompileOnly.get()) }
	named("testUtilsAnnotationProcessor") { extendsFrom(configurations.testAnnotationProcessor.get()) }
}

tasks.named<Test>("test") {
	description = "Runs unit tests."
	group = "verification"
	testClassesDirs = sourceSets["unitTest"].output.classesDirs
	classpath = sourceSets["unitTest"].runtimeClasspath
}

val integrationTest by tasks.registering(Test::class) {
	description = "Runs integration tests."
	group = "verification"
	testClassesDirs = sourceSets["integrationTest"].output.classesDirs
	classpath = sourceSets["integrationTest"].runtimeClasspath
}

val testAll by tasks.registering {
	description = "Runs all tests (unit and integration)."
	group = "verification"
	dependsOn(tasks.test, integrationTest)
}

tasks.withType<Test> {
	useJUnitPlatform()
}

idea {
	module {
		afterEvaluate {
			testSources.from(sourceSets["unitTest"].allJava.srcDirs)
			testSources.from(sourceSets["integrationTest"].allJava.srcDirs)
			testSources.from(sourceSets["testUtils"].allJava.srcDirs)
			testResources.from(sourceSets["unitTest"].resources.srcDirs)
			testResources.from(sourceSets["integrationTest"].resources.srcDirs)
			testResources.from(sourceSets["testUtils"].resources.srcDirs)
		}
	}
}
