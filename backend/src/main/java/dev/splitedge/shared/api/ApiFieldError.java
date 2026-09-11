package dev.splitedge.shared.api;

public record ApiFieldError(String field, ApiErrorCode code, String message) {}
