package ru.vikulinva.bff.screen;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * Если сосед не ответил, виноват не клиент. Наружу уходит 502 с внятным телом,
 * а не стек-трейс и не пятисотка «что-то пошло не так».
 */
@RestControllerAdvice
public class ScreenExceptionHandler {

    @ExceptionHandler(RestClientException.class)
    public ProblemDetail downstreamFailed(RestClientException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
            "Сервис-источник не ответил, экран собрать не удалось");
        problem.setProperty("code", "DOWNSTREAM_UNAVAILABLE");
        return problem;
    }
}
