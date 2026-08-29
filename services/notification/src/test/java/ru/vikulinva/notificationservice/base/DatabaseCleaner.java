package ru.vikulinva.notificationservice.base;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Утилита очистки БД между тестами. Не Spring-bean — создаётся вручную
 * в base-классе теста, чтобы не нужно было @ComponentScan на test-package.
 */
public class DatabaseCleaner {

    private final JdbcTemplate jdbc;

    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clearAll() {
        jdbc.execute("TRUNCATE TABLE delivery_attempts, notifications, processed_events RESTART IDENTITY CASCADE");
    }
}
