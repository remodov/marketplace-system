package ru.remodov.backoffice.catalog.exception;

import lombok.Getter;

@Getter
public class CatalogClientException extends CatalogException {
    private final int status;
    private final String code;

    public CatalogClientException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }
}
