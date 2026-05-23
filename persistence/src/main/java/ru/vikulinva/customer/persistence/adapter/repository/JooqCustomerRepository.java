package ru.vikulinva.customer.persistence.adapter.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Repository;
import ru.vikulinva.customer.core.customer.domain.aggregate.Customer;
import ru.vikulinva.customer.core.customer.domain.entity.VerificationToken;
import ru.vikulinva.customer.core.customer.domain.exception.OptimisticLockException;
import ru.vikulinva.customer.core.customer.domain.valueobject.CustomerId;
import ru.vikulinva.customer.core.customer.domain.valueobject.Email;
import ru.vikulinva.customer.core.customer.domain.valueobject.Phone;
import ru.vikulinva.customer.core.customer.domain.valueobject.VerificationTokenValue;
import ru.vikulinva.customer.core.customer.port.out.CustomerRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.selectFrom;
import static ru.vikulinva.customer.persistence.adapter.repository.SelectMultisetAliasKeys.TOKENS;
import static ru.vikulinva.customer.persistence.generated.Tables.CUSTOMER;
import static ru.vikulinva.customer.persistence.generated.Tables.VERIFICATION_TOKEN;

@Repository
@RequiredArgsConstructor
public class JooqCustomerRepository implements CustomerRepository {

    private final DSLContext dslContext;
    private final CustomerDomainRecordMapper mapper;
    private final OutboxEventWriter outboxEventWriter;

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return dslContext.select(
                        CUSTOMER.asterisk(),
                        multiset(selectFrom(VERIFICATION_TOKEN)
                                .where(VERIFICATION_TOKEN.CUSTOMER_ID.eq(CUSTOMER.ID)))
                                .as(TOKENS))
                .from(CUSTOMER)
                .where(CUSTOMER.ID.eq(id.value()))
                .fetchOptional()
                .map(this::mapAggregate);
    }

    @Override
    public Optional<Customer> findByEmail(Email email) {
        return dslContext.select(
                        CUSTOMER.asterisk(),
                        multiset(selectFrom(VERIFICATION_TOKEN)
                                .where(VERIFICATION_TOKEN.CUSTOMER_ID.eq(CUSTOMER.ID)))
                                .as(TOKENS))
                .from(CUSTOMER)
                .where(CUSTOMER.EMAIL.eq(email.value()))
                .fetchOptional()
                .map(this::mapAggregate);
    }

    @Override
    public Optional<Customer> findByVerificationToken(VerificationTokenValue token) {
        return dslContext.select(
                        CUSTOMER.asterisk(),
                        multiset(selectFrom(VERIFICATION_TOKEN)
                                .where(VERIFICATION_TOKEN.CUSTOMER_ID.eq(CUSTOMER.ID)))
                                .as(TOKENS))
                .from(CUSTOMER)
                .join(VERIFICATION_TOKEN).on(VERIFICATION_TOKEN.CUSTOMER_ID.eq(CUSTOMER.ID))
                .where(VERIFICATION_TOKEN.TOKEN.eq(token.value()))
                .fetchOptional()
                .map(this::mapAggregate);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return dslContext.fetchExists(CUSTOMER, CUSTOMER.EMAIL.eq(email.value()));
    }

    @Override
    public Customer save(Customer customer) {
        boolean exists = dslContext.fetchExists(CUSTOMER, CUSTOMER.ID.eq(customer.getId().value()));
        if (exists) {
            updateExisting(customer);
        } else {
            insertNew(customer);
        }
        mergeTokens(customer);
        outboxEventWriter.writeAll(customer.getEvents());
        customer.clearDomainEvents();
        return customer;
    }

    @Override
    public void delete(Customer customer) {
        dslContext.deleteFrom(CUSTOMER)
                .where(CUSTOMER.ID.eq(customer.getId().value()))
                .execute();
    }

    private void insertNew(Customer customer) {
        dslContext.insertInto(CUSTOMER)
                .set(CUSTOMER.ID, customer.getId().value())
                .set(CUSTOMER.EMAIL, customer.getEmail().value())
                .set(CUSTOMER.FIRST_NAME, customer.getProfile().firstName())
                .set(CUSTOMER.LAST_NAME, customer.getProfile().lastName())
                .set(CUSTOMER.PHONE, customer.getProfile().phone().map(Phone::value).orElse(null))
                .set(CUSTOMER.STATUS, mapper.toJooqStatus(customer.getStatus()))
                .set(CUSTOMER.CREATED_AT, customer.getCreatedAt().atOffset(ZoneOffset.UTC))
                .set(CUSTOMER.UPDATED_AT, customer.getUpdatedAt().atOffset(ZoneOffset.UTC))
                .set(CUSTOMER.VERSION, customer.getVersion())
                .execute();
    }

    private void updateExisting(Customer customer) {
        int affected = dslContext.update(CUSTOMER)
                .set(CUSTOMER.FIRST_NAME, customer.getProfile().firstName())
                .set(CUSTOMER.LAST_NAME, customer.getProfile().lastName())
                .set(CUSTOMER.PHONE, customer.getProfile().phone().map(Phone::value).orElse(null))
                .set(CUSTOMER.STATUS, mapper.toJooqStatus(customer.getStatus()))
                .set(CUSTOMER.UPDATED_AT, customer.getUpdatedAt().atOffset(ZoneOffset.UTC))
                .set(CUSTOMER.VERSION, customer.getVersion() + 1)
                .where(CUSTOMER.ID.eq(customer.getId().value()))
                .and(CUSTOMER.VERSION.eq(customer.getVersion()))
                .execute();
        if (affected != 1) {
            throw new OptimisticLockException(customer.getId());
        }
    }

    private void mergeTokens(Customer customer) {
        for (VerificationToken token : customer.getTokens()) {
            OffsetDateTime usedAt = token.getUsedAt() != null
                    ? token.getUsedAt().atOffset(ZoneOffset.UTC)
                    : null;
            dslContext.insertInto(VERIFICATION_TOKEN)
                    .set(VERIFICATION_TOKEN.TOKEN, token.getId().value())
                    .set(VERIFICATION_TOKEN.CUSTOMER_ID, customer.getId().value())
                    .set(VERIFICATION_TOKEN.ISSUED_AT, token.getIssuedAt().atOffset(ZoneOffset.UTC))
                    .set(VERIFICATION_TOKEN.EXPIRES_AT, token.getExpiresAt().atOffset(ZoneOffset.UTC))
                    .set(VERIFICATION_TOKEN.USED_AT, usedAt)
                    .onConflict(VERIFICATION_TOKEN.TOKEN)
                    .doUpdate()
                    .set(VERIFICATION_TOKEN.USED_AT, usedAt)
                    .execute();
        }
    }

    @SuppressWarnings("unchecked")
    private Customer mapAggregate(Record record) {
        var header = record.into(CUSTOMER).into(
                ru.vikulinva.customer.persistence.generated.tables.pojos.Customer.class);
        Result<Record> tokenRows = (Result<Record>) record.get(TOKENS);
        List<ru.vikulinva.customer.persistence.generated.tables.pojos.VerificationToken> tokens =
                tokenRows.into(ru.vikulinva.customer.persistence.generated.tables.pojos.VerificationToken.class);
        return mapper.assembleAggregate(header, tokens);
    }
}
