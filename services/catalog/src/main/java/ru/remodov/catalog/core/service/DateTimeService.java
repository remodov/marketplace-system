package ru.remodov.catalog.core.service;

import java.time.Instant;

@FunctionalInterface
public interface DateTimeService {
    Instant now();
}
