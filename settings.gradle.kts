// Монорепо: каждый сервис остаётся самостоятельной сборкой, а корень собирает их
// вместе (composite build). Так сервис можно открыть и запустить отдельно — как
// в жизни, где он живёт своим репозиторием.
rootProject.name = "marketplace-system"

includeBuild("services/catalog-starter")
includeBuild("services/catalog")
includeBuild("services/order")
includeBuild("services/payment")
includeBuild("services/customer")
includeBuild("services/notification")
includeBuild("services/backoffice")
