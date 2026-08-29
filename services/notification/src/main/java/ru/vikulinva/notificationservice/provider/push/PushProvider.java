package ru.vikulinva.notificationservice.provider.push;

/**
 * Outbound push-провайдер. На запуске — Firebase Cloud Messaging.
 * Без webhook'а: после успешной отправки статус {@code SENT} остаётся
 * терминальным с точки зрения сервиса (BR-N7).
 */
public interface PushProvider {

    Result send(String token, String title, String body);

    sealed interface Result {
        static Result ok() { return Ok.INSTANCE; }
        static Result transientError(String reason) { return new TransientError(reason); }
        static Result permanentError(String reason) { return new PermanentError(reason); }

        enum Ok implements Result { INSTANCE }
        record TransientError(String reason) implements Result {}
        record PermanentError(String reason) implements Result {}
    }
}
