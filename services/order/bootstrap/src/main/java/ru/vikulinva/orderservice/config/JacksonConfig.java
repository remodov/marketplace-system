package ru.vikulinva.orderservice.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson настройки для DomainEvent-сериализации в outbox: записываем
 * приватные поля напрямую (события — это record-style классы с
 * accessor-методами {@code customerId()}, не {@code getCustomerId()}).
 *
 * <p>Без этого payload содержал бы только поля базового класса
 * {@code DomainEvent} (id, aggregateId, aggregateType, createdAt) — все
 * специфичные данные события теряются.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer objectMapperCustomizer() {
        return builder -> builder.postConfigurer((ObjectMapper m) ->
            m.setVisibility(m.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)));
    }
}
