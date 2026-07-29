package com.example.authdemo.dto;

import com.example.authdemo.model.DailyCheck.Stav;
import lombok.Data;
import java.time.LocalDate;
import lombok.*;

@Data
public class DailyCheckForm {
    private LocalDate checkDate;
    private Stav overallResult;
    private String defectsDescription;
    private Long vehicleId;
}