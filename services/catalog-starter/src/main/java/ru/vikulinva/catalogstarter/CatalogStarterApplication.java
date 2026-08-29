package ru.vikulinva.catalogstarter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// TODO шаг 6: без включённого кэширования аннотации на сервисе ничего не делают.
@SpringBootApplication
public class CatalogStarterApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogStarterApplication.class, args);
    }
}
