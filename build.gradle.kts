//import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlin_version: String by extra
buildscript {
    var kotlin_version: String by extra
    kotlin_version = "1.2.51"
    repositories {
        mavenCentral()
    }
//    dependencies {
//        classpath(kotlinModule("gradle-plugin", kotlin_version))
//    }
}

plugins {
	application
	id("org.jetbrains.kotlin.jvm") version "1.2.51"
//	id ("com.github.johnrengelman.shadow") version "2.0.4"
//	id("io.spring.dependency-management") version "1.0.5.RELEASE"
}
//apply {
//    plugin("kotlin")
//}

// Tweak to be sure to have compiler and dependency versions the same
//extra["kotlin.version"] = plugins.getPlugin(KotlinPluginWrapper::class.java).kotlinPluginVersion

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

//dependencyManagement {
//	imports {
//		mavenBom("org.springframework.boot:spring-boot-dependencies:2.0.2.RELEASE")
//	}
//}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

//	implementation("org.springframework:spring-webflux")
//	implementation("org.springframework:spring-test")
//	implementation("org.springframework:spring-context") {
//		exclude(module = "spring-aop")
//	}
//	implementation("io.projectreactor.ipc:reactor-netty")

//	implementation("org.slf4j:slf4j-api")
	implementation("ch.qos.logback:logback-classic:1.0.13")

//	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
//	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.23.3")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:0.23.3")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:0.23.3")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:0.23.3")

    implementation("io.projectreactor:reactor-core:3.1.8.RELEASE")
//    implementation("io.projectreactor:reactor-core")

    implementation ("com.netflix.hystrix:hystrix-core:1.5.12")
    implementation("io.reactivex:rxjava-reactive-streams:1.2.1")  // used by Hystrix

//    testImplementation("io.projectreactor:reactor-test:3.1.8.RELEASE")
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
