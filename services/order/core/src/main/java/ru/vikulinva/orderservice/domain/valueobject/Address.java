package ru.vikulinva.orderservice.domain.valueobject;

import ru.vikulinva.ddd.ValueObject;

import java.util.Objects;

/**
 * Доставочный адрес. PII — шифруется в БД.
 *
 * @param country    код страны ISO 3166-1 alpha-2 (например, "RU")
 * @param city       город
 * @param street     улица + дом + квартира
 * @param postalCode индекс
 * @param pickupCode код ПВЗ, если выбран ПВЗ; иначе null
 */
public record Address(String country, String city, String street, String postalCode, String pickupCode)
    implements ValueObject {

    public Address {
        Objects.requireNonNull(country, "Address.country must not be null");
        Objects.requireNonNull(city, "Address.city must not be null");
        Objects.requireNonNull(street, "Address.street must not be null");
        Objects.requireNonNull(postalCode, "Address.postalCode must not be null");
        if (country.length() != 2) {
            throw new IllegalArgumentException("Address.country must be ISO 3166-1 alpha-2: " + country);
        }
    }
}
