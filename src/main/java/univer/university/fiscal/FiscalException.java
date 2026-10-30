package univer.university.fiscal;

public class FiscalException extends RuntimeException {

    private final boolean retryable;

    public FiscalException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
