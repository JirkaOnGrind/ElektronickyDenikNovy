package com.example.authdemo.repository;

import com.example.authdemo.model.DailyCheck;
import com.example.authdemo.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyCheckRepository extends JpaRepository<DailyCheck, Long> {
    List<DailyCheck> findByVehicleId(Long vehicleId);
    List<DailyCheck> findByUserId(Long userId);
    List<DailyCheck> findByCheckDateBetween(LocalDate startDate, LocalDate endDate);
    List<DailyCheck> findByVehicleIdAndCheckDateBetween(Long vehicleId, LocalDate startDate, LocalDate endDate);
    boolean existsByVehicleIdAndCheckDate(Long vehicleId, LocalDate checkDate);
    List<DailyCheck> findByVehicleAndCheckDateBetweenOrderByCheckDateDescIdDesc(Vehicle vehicle, LocalDate startDate, LocalDate endDate);
    List<DailyCheck> findByVehicleAndOverallResultOrderByCheckDateDescIdDesc(Vehicle vehicle, DailyCheck.Stav overallResult);

    Optional<DailyCheck> findTopByVehicleAndOverallResultOrderByCheckDateDesc(Vehicle vehicle, DailyCheck.Stav overallResult);
}
