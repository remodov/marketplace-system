package ru.vikulinva.customer.testing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public final class CustomerTestObjectGenerator {

    public static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID OTHER_CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static final String EMAIL = "buyer@example.com";
    public static final String FIRST_NAME = "Ivan";
    public static final String LAST_NAME = "Petrov";
    public static final String PHONE = "+79991234567";

    public static final String VERIFICATION_TOKEN = "abcdefghijklmnopqrstuvwxyz012345";

    public static final OffsetDateTime FIXED_NOW = OffsetDateTime
            .parse("2026-05-23T10:00:00Z")
            .withOffsetSameInstant(ZoneOffset.UTC);

    private CustomerTestObjectGenerator() {
    }
}
