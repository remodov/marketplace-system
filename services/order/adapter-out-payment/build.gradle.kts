plugins {
    `java-library`
}

dependencies {
    api(project(":core"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.resilience4j.spring.boot3)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.wiremock)
    testImplementation(libs.wiremock.jetty12)
}
