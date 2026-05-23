package ru.remodov.backoffice.moderation.exception;

public class CatalogUnavailableException extends BackofficeException {

    public CatalogUnavailableException(Throwable cause) {
        super("Catalog unavailable", cause);
    }

    @Override
    public String code() {
        return "CATALOG_UNAVAILABLE";
    }

    @Override
    public int status() {
        return 503;
    }
}
