package ru.remodov.backoffice.moderation.exception;

public class InvalidReasonException extends BackofficeException {

    public InvalidReasonException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "INVALID_REASON";
    }

    @Override
    public int status() {
        return 400;
    }
}
