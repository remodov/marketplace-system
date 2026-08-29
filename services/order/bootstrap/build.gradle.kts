plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":adapter-in-rest"))
    implementation(project(":adapter-in-kafka"))
    implementation(project(":adapter-out-postgres"))
    implementation(project(":adapter-out-payment"))
    implementation(project(":adapter-out-catalog"))
    implementation(project(":adapter-out-kafka"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.actuator)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation(libs.spring.boot.starter.cache)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.usecase.pattern.starter)
    implementation(libs.micrometer.prometheus)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.kafka)
    testImplementation("org.springframework.security:spring-security-test:6.4.2")
    testImplementation(project(":test-utils"))
}

tasks.named<Jar>("jar") {
    enabled = false
}

springBoot {
    mainClass.set("ru.vikulinva.orderservice.OrderServiceApplication")
}
