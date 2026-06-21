package com.experimentms.exception;

public class ApiException extends RuntimeException {
    private final int status;
    private final Object detail;

    public ApiException(int status, Object detail) {
        super(String.valueOf(detail));
        this.status = status;
        this.detail = detail;
    }

    public int getStatus() {
        return status;
    }

    public Object getDetail() {
        return detail;
    }
}
