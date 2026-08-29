package ru.remodov.backoffice.core.service;

import java.util.UUID;

@FunctionalInterface
public interface UuidGenerator {
    UUID generate();
}
