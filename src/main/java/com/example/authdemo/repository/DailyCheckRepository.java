package com.example.authdemo.repository;

import com.example.authdemo.model.DailyCheck;
import com.example.authdemo.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyCheckRepository extends JpaRepository<DailyCheck, Long> {
    List<DailyCheck> findByVehicleId(Long vehicleId);
    List<DailyCheck> findByUserId(Long userId);
    List<DailyCheck> findByCheckDateBetween(LocalDate startDate, LocalDate endDate);
    List<DailyCheck> findByVehicleIdAndCheckDateBetween(Long vehicleId, LocalDate startDate, LocalDate endDate);
    boolean existsByVehicleIdAndCheckDate(Long vehicleId, LocalDate checkDate);

    default boolean existsTodayByVehicleId(Long vehicleId) {
        return existsByVehicleIdAndCheckDate(vehicleId, LocalDate.now());
    }

    List<DailyCheck> findByCheckDateAndVehicleIdIn(LocalDate checkDate, List<Long> vehicleIds);

    List<DailyCheck> findByVehicleAndCheckDateBetweenOrderByCheckDateDescIdDesc(Vehicle vehicle, LocalDate startDate, LocalDate endDate);
    List<DailyCheck> findByVehicleAndOverallResultOrderByCheckDateDescIdDesc(Vehicle vehicle, DailyCheck.Stav overallResult);

    Optional<DailyCheck> findTopByVehicleAndOverallResultOrderByCheckDateDesc(Vehicle vehicle, DailyCheck.Stav overallResult);
    @Query("select dc from DailyCheck dc left join fetch dc.user join fetch dc.vehicle v "
            + "where dc.overallResult = :overallResult and v.companyKey = :companyKey "
            + "order by dc.createdAt desc")
    List<DailyCheck> findRecentDefectsByCompany(
            @Param("overallResult") DailyCheck.Stav overallResult,
            @Param("companyKey") String companyKey,
            Pageable pageable);

    @Query("select dc from DailyCheck dc left join fetch dc.user join fetch dc.vehicle "
            + "where dc.overallResult = :overallResult "
            + "order by dc.createdAt desc")
    List<DailyCheck> findRecentDefects(
            @Param("overallResult") DailyCheck.Stav overallResult,
            Pageable pageable);
}
