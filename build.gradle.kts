plugins {
    val kotlinVersion = "2.3.21"

    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion

    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "1.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "cash.atto"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

dependencies {
    val commonsVersion = "6.7.0-SNAPSHOT"
    val cucumberVersion = "7.34.3"
    val springdocVersion = "3.0.3"
    val swaggerCoreVersion = "2.2.50"
    val testcontainersVersion = "2.0.5"

    constraints {
        implementation("io.swagger.core.v3:swagger-core-jakarta:$swaggerCoreVersion") {
            because("Springdoc 3.0.3 pulls 2.2.47, which emits invalid OpenAPI 3.1 schema defaults for request bodies.")
        }
    }

    implementation("cash.atto:commons-spring-boot-starter:$commonsVersion")

    implementation("cash.atto:commons-node-remote:$commonsVersion")

    implementation("cash.atto:commons-wallet:$commonsVersion")

    implementation("cash.atto:commons-worker-remote:$commonsVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:$springdocVersion")

    implementation("com.github.ben-manes.caffeine:caffeine")

    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    implementation("io.asyncer:r2dbc-mysql:1.4.2")
    implementation("com.mysql:mysql-connector-j")
    implementation("org.flywaydb:flyway-mysql")

    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito")
    }
    testImplementation("cash.atto:commons-test:$commonsVersion")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("io.projectreactor:reactor-test")

    testImplementation("org.junit.platform:junit-platform-suite") // for cucumber
    testImplementation("io.cucumber:cucumber-java:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-spring:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    testImplementation("org.awaitility:awaitility:4.3.0")

    testImplementation(platform("org.testcontainers:testcontainers-bom:$testcontainersVersion"))
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("org.testcontainers:testcontainers-r2dbc")
    implementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

ktlint {
    version.set("1.4.1")
}

graalvmNative {
    binaries {
        named("main") {
        }
    }
}
