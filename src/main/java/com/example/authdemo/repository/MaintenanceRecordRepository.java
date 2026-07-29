package com.example.authdemo.repository;

import com.example.authdemo.model.MaintenanceRecord;
import com.example.authdemo.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {
    List<MaintenanceRecord> findByVehicleAndMaintenanceDateBetweenOrderByMaintenanceDateDescIdDesc(
            Vehicle vehicle,
            LocalDate startDate,
            LocalDate endDate
    );

    List<MaintenanceRecord> findByVehicleAndResultOrderByMaintenanceDateDescIdDesc(
            Vehicle vehicle,
            MaintenanceRecord.MaintenanceResult result
    );
}
