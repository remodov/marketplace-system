val lombokVersion: String by rootProject
val springBootVersion: String by rootProject

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence"))

    implementation("org.springframework.boot:spring-boot-starter:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-jooq:$springBootVersion")
    implementation("org.springframework.kafka:spring-kafka:3.3.1")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.kafka:spring-kafka-test:3.3.1")
}
