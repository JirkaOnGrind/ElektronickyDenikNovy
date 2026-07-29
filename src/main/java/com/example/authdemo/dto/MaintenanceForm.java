package com.example.authdemo.dto;

import com.example.authdemo.model.MaintenanceRecord.MaintenanceResult;
import lombok.Data;
import java.time.LocalDate;

@Data
public class MaintenanceForm {
    private LocalDate maintenanceDate;
    private LocalDate nextRevisionDate; // Nové pole
    private MaintenanceResult result;
    private String description;
    private Long vehicleId;
}