package ru.remodov.backoffice.moderation.exception;

public class InvalidNoteException extends BackofficeException {

    public InvalidNoteException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "INVALID_NOTE";
    }

    @Override
    public int status() {
        return 400;
    }
}
