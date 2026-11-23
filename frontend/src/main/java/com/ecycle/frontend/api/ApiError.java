package com.ecycle.frontend.api;

import java.util.Map;

public class ApiError extends RuntimeException {
    private final int status; private final Map<String, String> fieldErrors;
    public ApiError(int status, String message, Map<String, String> fieldErrors) { super(message); this.status = status; this.fieldErrors = fieldErrors == null ? Map.of() : fieldErrors; }
    public int status() { return status; }
    public Map<String, String> fieldErrors() { return fieldErrors; }
    /** Human-readable text including per-field validation messages. */
    public String display() {
        if (fieldErrors.isEmpty()) return getMessage();
        StringBuilder sb = new StringBuilder(getMessage());
        fieldErrors.forEach((k, v) -> sb.append("\n- ").append(k).append(": ").append(v));
        return sb.toString();
    }
}
