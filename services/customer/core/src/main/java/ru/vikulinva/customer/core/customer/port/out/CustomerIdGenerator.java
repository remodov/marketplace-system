package ru.vikulinva.customer.core.customer.port.out;

import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;

public interface CustomerIdGenerator {

    CustomerId generate();
}
