plugins {
    `java-library`
}

dependencies {
    api(project(":core"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.kafka)
    implementation(libs.usecase.pattern.starter)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
}
