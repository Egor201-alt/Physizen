package dev.egor201.physizen.core;

public final class PhysException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PhysException(String message) {
        super(message);
    }

    public PhysException(String message, Throwable cause) {
        super(message, cause);
    }
}
