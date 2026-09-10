package com.example.authdemo.repository;

import com.example.authdemo.model.DailyCheck;
import com.example.authdemo.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DailyCheckRepository extends JpaRepository<DailyCheck, Long> {
    List<DailyCheck> findByVehicleId(Long vehicleId);
    List<DailyCheck> findByUserId(Long userId);
    List<DailyCheck> findByCheckDateBetween(LocalDate startDate, LocalDate endDate);
    List<DailyCheck> findByVehicleIdAndCheckDateBetween(Long vehicleId, LocalDate startDate, LocalDate endDate);
    boolean existsByVehicleIdAndCheckDate(Long vehicleId, LocalDate checkDate);

    default boolean existsTodayByVehicleId(Long vehicleId) {
        return existsByVehicleIdAndCheckDate(vehicleId, LocalDate.now());
    }

    @Query("SELECT DISTINCT dc.vehicle.id FROM DailyCheck dc WHERE dc.checkDate = :checkDate AND dc.vehicle.id IN :vehicleIds")
    Set<Long> findCheckedVehicleIds(@Param("checkDate") LocalDate checkDate,
                                    @Param("vehicleIds") Collection<Long> vehicleIds);

    List<DailyCheck> findByVehicleAndCheckDateBetweenOrderByCheckDateDescIdDesc(Vehicle vehicle, LocalDate startDate, LocalDate endDate);
    List<DailyCheck> findByVehicleAndOverallResultOrderByCheckDateDescIdDesc(Vehicle vehicle, DailyCheck.Stav overallResult);

    Optional<DailyCheck> findTopByVehicleAndOverallResultOrderByCheckDateDesc(Vehicle vehicle, DailyCheck.Stav overallResult);
    List<DailyCheck> findTop10ByOverallResultAndVehicleCompanyKeyOrderByCreatedAtDesc(DailyCheck.Stav overallResult, String companyKey);
    List<DailyCheck> findTop10ByOverallResultOrderByCreatedAtDesc(DailyCheck.Stav overallResult);
}
