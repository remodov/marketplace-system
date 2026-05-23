package ru.vikulinva.customer.testing;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import ru.vikulinva.customer.persistence.generated.enums.CustomerStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

import static ru.vikulinva.customer.persistence.generated.Tables.CUSTOMER;
import static ru.vikulinva.customer.persistence.generated.Tables.OUTBOX;
import static ru.vikulinva.customer.persistence.generated.Tables.VERIFICATION_TOKEN;

@Component
@RequiredArgsConstructor
public class CustomerDatabasePreparer {

    private final DSLContext dsl;

    public void clearAll() {
        dsl.deleteFrom(OUTBOX).execute();
        dsl.deleteFrom(VERIFICATION_TOKEN).execute();
        dsl.deleteFrom(CUSTOMER).execute();
    }

    public void insertCustomer(UUID id,
                               String email,
                               String firstName,
                               String lastName,
                               String phone,
                               CustomerStatus status,
                               OffsetDateTime createdAt,
                               OffsetDateTime updatedAt) {
        dsl.insertInto(CUSTOMER)
                .set(CUSTOMER.ID, id)
                .set(CUSTOMER.EMAIL, email)
                .set(CUSTOMER.FIRST_NAME, firstName)
                .set(CUSTOMER.LAST_NAME, lastName)
                .set(CUSTOMER.PHONE, phone)
                .set(CUSTOMER.STATUS, status)
                .set(CUSTOMER.CREATED_AT, createdAt)
                .set(CUSTOMER.UPDATED_AT, updatedAt)
                .set(CUSTOMER.VERSION, 0L)
                .execute();
    }

    public void insertToken(String token,
                            UUID customerId,
                            OffsetDateTime issuedAt,
                            OffsetDateTime expiresAt,
                            OffsetDateTime usedAt) {
        dsl.insertInto(VERIFICATION_TOKEN)
                .set(VERIFICATION_TOKEN.TOKEN, token)
                .set(VERIFICATION_TOKEN.CUSTOMER_ID, customerId)
                .set(VERIFICATION_TOKEN.ISSUED_AT, issuedAt)
                .set(VERIFICATION_TOKEN.EXPIRES_AT, expiresAt)
                .set(VERIFICATION_TOKEN.USED_AT, usedAt)
                .execute();
    }

    public boolean hasOutboxEvent(String eventType, UUID aggregateId) {
        return dsl.fetchExists(OUTBOX,
                OUTBOX.EVENT_TYPE.eq(eventType).and(OUTBOX.AGGREGATE_ID.eq(aggregateId)));
    }

    public String fetchOutboxPayload(String eventType, UUID aggregateId) {
        return dsl.select(OUTBOX.PAYLOAD)
                .from(OUTBOX)
                .where(OUTBOX.EVENT_TYPE.eq(eventType))
                .and(OUTBOX.AGGREGATE_ID.eq(aggregateId))
                .fetchOne(OUTBOX.PAYLOAD)
                .data();
    }

    public CustomerStatus fetchCustomerStatus(UUID id) {
        return dsl.select(CUSTOMER.STATUS)
                .from(CUSTOMER)
                .where(CUSTOMER.ID.eq(id))
                .fetchOne(CUSTOMER.STATUS);
    }
}
