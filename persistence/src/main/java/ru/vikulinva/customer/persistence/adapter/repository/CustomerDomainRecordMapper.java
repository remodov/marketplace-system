package ru.vikulinva.customer.persistence.adapter.repository;

import org.springframework.stereotype.Component;
import ru.vikulinva.customer.core.customer.domain.aggregate.Customer;
import ru.vikulinva.customer.core.customer.domain.entity.VerificationToken;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;
import ru.vikulinva.customer.core.customer.domain.valueobject.Email;
import ru.vikulinva.customer.core.customer.domain.valueobject.Name;
import ru.vikulinva.customer.core.customer.domain.valueobject.Phone;
import ru.vikulinva.customer.core.customer.domain.valueobject.Profile;
import ru.vikulinva.customer.core.customer.domain.valueobject.Status;
import ru.vikulinva.customer.core.customer.domain.valueobject.VerificationTokenValue;
import ru.vikulinva.customer.persistence.generated.enums.CustomerStatus;

import java.util.List;
import java.util.Optional;

@Component
public class CustomerDomainRecordMapper {

    public Customer assembleAggregate(
            ru.vikulinva.customer.persistence.generated.tables.pojos.Customer header,
            List<ru.vikulinva.customer.persistence.generated.tables.pojos.VerificationToken> tokenRows) {
        List<VerificationToken> tokens = tokenRows.stream()
                .map(this::toDomainToken)
                .toList();
        Profile profile = new Profile(
                new Name(header.getFirstName(), header.getLastName()),
                Optional.ofNullable(header.getPhone()).map(Phone::of)
        );
        return Customer.rehydrate(
                CustomerId.of(header.getId()),
                Email.of(header.getEmail()),
                profile,
                Status.valueOf(header.getStatus().name()),
                header.getCreatedAt().toInstant(),
                header.getUpdatedAt().toInstant(),
                header.getVersion(),
                tokens
        );
    }

    public VerificationToken toDomainToken(
            ru.vikulinva.customer.persistence.generated.tables.pojos.VerificationToken pojo) {
        return VerificationToken.rehydrate(
                VerificationTokenValue.of(pojo.getToken()),
                pojo.getIssuedAt().toInstant(),
                pojo.getExpiresAt().toInstant(),
                pojo.getUsedAt() != null ? pojo.getUsedAt().toInstant() : null
        );
    }

    public CustomerStatus toJooqStatus(Status status) {
        return CustomerStatus.valueOf(status.name());
    }
}
