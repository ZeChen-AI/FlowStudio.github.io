package studio.flow.dto;

public record HealthResponse(String status, boolean mockRunner, String autodlBaseUrl, boolean autodlConfigured) {}
