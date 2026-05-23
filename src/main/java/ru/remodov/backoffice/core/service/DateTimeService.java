package ru.remodov.backoffice.core.service;

import java.time.Instant;

@FunctionalInterface
public interface DateTimeService {
    Instant now();
}
