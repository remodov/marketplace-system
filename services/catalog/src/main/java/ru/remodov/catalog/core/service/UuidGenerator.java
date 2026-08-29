package ru.remodov.catalog.core.service;

import java.util.UUID;

@FunctionalInterface
public interface UuidGenerator {
    UUID generate();
}
