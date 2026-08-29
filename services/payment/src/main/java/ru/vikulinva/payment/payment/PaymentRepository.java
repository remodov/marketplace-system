package ru.vikulinva.payment.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/**
 * Голый JDBC: SQL руками, маппинг строки в объект руками. Ни JPA, ни jOOQ —
 * чтобы было видно, что именно они делают за тебя в других сервисах.
 */
@Repository
public class PaymentRepository {

    private static final RowMapper<Payment> MAPPER = (rs, rowNum) -> new Payment(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("order_id")),
        rs.getBigDecimal("amount"),
        rs.getString("currency"),
        Payment.Status.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant()
    );

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Payment payment) {
        jdbc.update("""
            insert into payments (id, order_id, amount, currency, status, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """,
            payment.id().toString(), payment.orderId().toString(), payment.amount(), payment.currency(),
            payment.status().name(), Timestamp.from(payment.createdAt()), Timestamp.from(payment.updatedAt()));
    }

    public Optional<Payment> findById(UUID id) {
        return jdbc.query("select * from payments where id = ?", MAPPER, id.toString()).stream().findFirst();
    }

    public Optional<Payment> findByOrderId(UUID orderId) {
        return jdbc.query("select * from payments where order_id = ?", MAPPER, orderId.toString())
            .stream().findFirst();
    }

    public void updateStatus(Payment payment) {
        jdbc.update("update payments set status = ?, updated_at = ? where id = ?",
            payment.status().name(), Timestamp.from(payment.updatedAt()), payment.id().toString());
    }
}
