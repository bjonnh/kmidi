import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    application
    kotlin("jvm") version "1.2.51"
}

group = "net.bjonnh.audio"
version = "1.0-SNAPSHOT"


apply {
    plugin("kotlin")
    plugin("distribution")
}


repositories {
    mavenCentral()
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("io.reactivex.rxjava2:rxkotlin:2.2.0")
    testCompile("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}


application {
    group = "net.bjonnh.audio"
    version = version
    applicationName = "kmidi"
    mainClassName = "net.bjonnh.audio.kmidi.MainKt"
}
