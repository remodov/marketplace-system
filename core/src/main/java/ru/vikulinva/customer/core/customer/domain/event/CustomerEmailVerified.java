package ru.vikulinva.customer.core.customer.domain.event;

import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class CustomerEmailVerified extends DomainEvent {

    public static final String AGGREGATE_TYPE = "Customer";

    private final UUID customerId;
    private final String email;
    private final Instant verifiedAt;

    public CustomerEmailVerified(UUID customerId, String email, Instant verifiedAt) {
        super(AGGREGATE_TYPE, customerId.toString());
        this.customerId = customerId;
        this.email = email;
        this.verifiedAt = verifiedAt;
    }
}
