package ru.vikulinva.orderservice.usecase.command.exception;

/** BR-014. */
public final class MultiSellerNotSupportedException extends OrderDomainException {

    public MultiSellerNotSupportedException(int sellerCount) {
        super("MULTI_SELLER_NOT_SUPPORTED", 400,
            "Order must contain items from a single seller, found " + sellerCount);
    }
}
