import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlin_version: String by extra
buildscript {
    var kotlin_version: String by extra
    kotlin_version = "1.2.51"
    repositories {
        mavenCentral()
    }
}

plugins {
	application
	id("org.jetbrains.kotlin.jvm") version "1.2.51"
}

repositories {
	mavenCentral()
}

application {
	mainClassName = "examples.KotlinExampleKt"
}

tasks {
	withType<KotlinCompile> {
		kotlinOptions {
			jvmTarget = "1.8"
			freeCompilerArgs = listOf("-Xjsr305=strict")
		}
	}
}

val test by tasks.getting(Test::class) {
	useJUnitPlatform()
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	implementation("ch.qos.logback:logback-classic:1.0.13")

    implementation("io.projectreactor:reactor-core:3.1.8.RELEASE")

    implementation ("com.netflix.hystrix:hystrix-core:1.5.12")
    implementation("io.reactivex:rxjava-reactive-streams:1.2.1")  // used by Hystrix

    testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.assertj:assertj-core")

	testImplementation("org.junit.jupiter:junit-jupiter-api")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
