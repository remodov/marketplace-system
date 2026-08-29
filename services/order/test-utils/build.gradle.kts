plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
    api(project(":adapter-out-postgres"))

    api(libs.jooq)
    api(libs.spring.boot.starter.test)
    api(libs.spring.boot.testcontainers)
    api(libs.testcontainers.postgres)
    api(libs.testcontainers.junit.jupiter)
    api(libs.wiremock)
    api(libs.wiremock.jetty12)
    api(libs.junit.jupiter)
    api(libs.assertj)
    api(libs.mockito.core)
    api(libs.mockito.junit.jupiter)
    api(libs.archunit.junit5)
    api(libs.hexagonal.architecture.test)
}
