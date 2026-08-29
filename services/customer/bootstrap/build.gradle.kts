plugins {
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

val lombokVersion: String by rootProject
val resilience4jVersion: String by rootProject
val testcontainersVersion: String by rootProject
val archunitVersion: String by rootProject

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence"))
    implementation(project(":user-in-adapter"))
    implementation(project(":kafka-out-adapter"))

    implementation("ru.vikulinva:usecase-pattern-starter:1.1.0")
    implementation("ru.vikulinva:ddd-building-blocks:1.0.0")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")

    implementation("io.github.resilience4j:resilience4j-spring-boot3:$resilience4jVersion")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test:6.4.2")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.jooq:jooq")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

springBoot {
    mainClass.set("ru.vikulinva.customer.bootstrap.CustomerApplication")
}

sourceSets.named("main") {
    resources.srcDir(rootProject.file("migrations"))
}

tasks.named("checkstyleMain") {
    enabled = false
}
