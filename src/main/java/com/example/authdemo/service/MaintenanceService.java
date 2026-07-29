package com.example.authdemo.service;

import com.example.authdemo.model.MaintenanceRecord;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.MaintenanceRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class MaintenanceService {

    @Autowired
    private MaintenanceRecordRepository maintenanceRepository;

    public MaintenanceRecord save(MaintenanceRecord record) {
        return maintenanceRepository.save(record);
    }

    public Optional<MaintenanceRecord> findById(Long id) {
        return maintenanceRepository.findById(id);
    }

    public List<MaintenanceRecord> findRecordsByVehicleAndDateRange(Vehicle vehicle, LocalDate startDate, LocalDate endDate) {
        return maintenanceRepository.findByVehicleAndMaintenanceDateBetweenOrderByMaintenanceDateDescIdDesc(
                vehicle,
                startDate,
                endDate
        );
    }

    public List<MaintenanceRecord> findDefectsByVehicle(Vehicle vehicle) {
        return maintenanceRepository.findByVehicleAndResultOrderByMaintenanceDateDescIdDesc(
                vehicle,
                MaintenanceRecord.MaintenanceResult.ZAVAD
        );
    }
}
