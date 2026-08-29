plugins {
    `java-library`
}

dependencies {
    api(libs.usecase.pattern.core)
    api(libs.ddd.building.blocks)
    api(libs.hexagonal.architecture.core)

    // Только Handler-ы из usecase/* нуждаются в Spring-аннотациях @Component / @Transactional.
    // Хранятся как compileOnly — runtime приходит через bootstrap → adapter-* → Spring Boot.
    // Domain-пакет инвариантно не использует Spring (проверяется HexagonalArchitectureRules).
    compileOnly("org.springframework:spring-context:6.2.1")
    compileOnly("org.springframework:spring-tx:6.2.1")

    // MapStruct compile-time only — нужен здесь, потому что DTO/маппинг живут рядом с UseCase
    compileOnly(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation("org.springframework:spring-context:6.2.1")
}
