package ru.vikulinva.notificationservice.provider.email;

/**
 * Outbound email-провайдер. Реализация — Mailgun, заменяемо на SES/SendGrid
 * без переписывания вызывающего кода.
 */
public interface EmailProvider {

    /**
     * Отправляет письмо. На успех возвращает {@link Result#ok(String)} с
     * {@code externalId} провайдера (нужен для матчинга webhook'ов).
     *
     * <p>Сетевые ошибки и 5xx → {@link Result#transientError}.
     * 4xx (невалидный email и т.п.) → {@link Result#permanentError}.
     */
    Result send(String to, String subject, String htmlBody);

    sealed interface Result {
        static Result ok(String externalId) { return new Ok(externalId); }
        static Result transientError(String reason) { return new TransientError(reason); }
        static Result permanentError(String reason) { return new PermanentError(reason); }

        record Ok(String externalId) implements Result {}
        record TransientError(String reason) implements Result {}
        record PermanentError(String reason) implements Result {}
    }
}
