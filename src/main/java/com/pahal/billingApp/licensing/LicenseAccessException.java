package com.pahal.billingApp.licensing;
public class LicenseAccessException extends org.springframework.security.access.AccessDeniedException {
    private final String code;
    public LicenseAccessException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
