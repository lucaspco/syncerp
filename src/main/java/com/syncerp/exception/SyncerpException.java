package com.syncerp.exception;

public class SyncerpException extends RuntimeException {

    private String errorCode;
    private String details;

    public SyncerpException(String message) {
        super(message);
        this.errorCode = "SYNCERP_ERROR";
    }

    public SyncerpException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "SYNCERP_ERROR";
    }

    public SyncerpException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SyncerpException(String errorCode, String message, String details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDetails() {
        return details;
    }

}
