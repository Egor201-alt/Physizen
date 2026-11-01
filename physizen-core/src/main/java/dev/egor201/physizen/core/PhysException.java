package dev.egor201.physizen.core;

public final class PhysException extends RuntimeException {

    public PhysException(String message) {
        super(message);
    }

    public PhysException(String message, Throwable cause) {
        super(message, cause);
    }
}
