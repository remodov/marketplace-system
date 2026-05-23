package ru.vikulinva.customer.core.customer.domain.event;

import lombok.Getter;
import ru.vikulinva.ddd.DomainEvent;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class CustomerProfileUpdated extends DomainEvent {

    public static final String AGGREGATE_TYPE = "Customer";

    private final UUID customerId;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String phone;
    private final Instant updatedAt;

    public CustomerProfileUpdated(UUID customerId,
                                  String email,
                                  String firstName,
                                  String lastName,
                                  String phone,
                                  Instant updatedAt) {
        super(AGGREGATE_TYPE, customerId.toString());
        this.customerId = customerId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.updatedAt = updatedAt;
    }
}
