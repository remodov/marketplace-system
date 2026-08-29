// Корневых исходников нет — только задачи, которые прогоняют все сервисы разом.
tasks.register("buildAll") {
    group = "marketplace"
    description = "Собрать все сервисы"
    dependsOn(gradle.includedBuilds.map { it.task(":build") })
}

tasks.register("testAll") {
    group = "marketplace"
    description = "Прогнать тесты всех сервисов"
    dependsOn(gradle.includedBuilds.map { it.task(":test") })
}
