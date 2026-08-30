val springBootVersion: String by rootProject

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence"))

    implementation("org.springframework.boot:spring-boot-starter:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-jooq:$springBootVersion")
    implementation("org.springframework.kafka:spring-kafka:3.3.1")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.kafka:spring-kafka-test:3.3.1")
}
