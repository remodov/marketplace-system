plugins {
    `java-library`
}

dependencies {
    api(project(":core"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.resilience4j.spring.boot3)
    // Без AOP аннотации @Retry и @CircuitBreaker не срабатывают вообще.
    implementation(libs.spring.boot.starter.aop)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.wiremock)
    testImplementation(libs.wiremock.jetty12)
}
