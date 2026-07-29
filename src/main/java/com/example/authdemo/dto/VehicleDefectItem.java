package com.example.authdemo.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VehicleDefectItem(
        String sourceLabel,
        LocalDate eventDate,
        LocalDateTime createdAt,
        String reporterName,
        String description
) {
    public boolean isBackfilled() {
        return createdAt != null && createdAt.toLocalDate().isAfter(eventDate);
    }
}
