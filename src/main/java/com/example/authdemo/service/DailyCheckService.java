package com.example.authdemo.service;

import com.example.authdemo.model.DailyCheck;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.DailyCheckRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DailyCheckService {

    @Autowired
    private DailyCheckRepository dailyCheckRepository;

    public DailyCheck saveDailyCheck(DailyCheck dailyCheck) {
        return dailyCheckRepository.save(dailyCheck);
    }

    @Transactional
    public synchronized Optional<DailyCheck> saveDailyCheckIfAbsent(DailyCheck dailyCheck) {
        Long vehicleId = dailyCheck.getVehicle().getId();
        LocalDate checkDate = dailyCheck.getCheckDate();

        if (dailyCheckRepository.existsByVehicleIdAndCheckDate(vehicleId, checkDate)) {
            return Optional.empty();
        }

        return Optional.of(dailyCheckRepository.saveAndFlush(dailyCheck));
    }

    public Optional<DailyCheck> getDailyCheckById(Long id) {
        return dailyCheckRepository.findById(id);
    }

    public List<DailyCheck> getDailyChecksByVehicle(Long vehicleId) {
        return dailyCheckRepository.findByVehicleId(vehicleId);
    }

    public List<DailyCheck> getDailyChecksByDateRange(LocalDate startDate, LocalDate endDate) {
        return dailyCheckRepository.findByCheckDateBetween(startDate, endDate);
    }

    public List<DailyCheck> getDailyChecksByVehicleAndDateRange(Long vehicleId, LocalDate startDate, LocalDate endDate) {
        return dailyCheckRepository.findByVehicleIdAndCheckDateBetween(vehicleId, startDate, endDate);
    }

    public List<DailyCheck> findChecksByVehicleAndDateRange(Vehicle vehicle, LocalDate startDate, LocalDate endDate) {
        return dailyCheckRepository.findByVehicleAndCheckDateBetweenOrderByCheckDateDescIdDesc(vehicle, startDate, endDate);
    }

    public boolean existsDailyCheckForVehicleAndDate(Long vehicleId, LocalDate date) {
        return dailyCheckRepository.existsByVehicleIdAndCheckDate(vehicleId, date);
    }

    public boolean existsDailyCheckForVehicleToday(Long vehicleId) {
        return dailyCheckRepository.existsTodayByVehicleId(vehicleId);
    }

    public Set<Long> getCheckedVehicleIdsToday(List<Vehicle> vehicles) {
        List<Long> vehicleIds = vehicles.stream()
                .map(Vehicle::getId)
                .toList();

        if (vehicleIds.isEmpty()) {
            return Collections.emptySet();
        }

        return dailyCheckRepository.findByCheckDateAndVehicleIdIn(LocalDate.now(), vehicleIds).stream()
                .map(check -> check.getVehicle().getId())
                .collect(Collectors.toSet());
    }

    public Optional<DailyCheck> findLastDefect(Vehicle vehicle) {
        return dailyCheckRepository.findTopByVehicleAndOverallResultOrderByCheckDateDesc(vehicle, DailyCheck.Stav.ZAVAD);
    }

    public List<DailyCheck> findDefectsByVehicle(Vehicle vehicle) {
        return dailyCheckRepository.findByVehicleAndOverallResultOrderByCheckDateDescIdDesc(
                vehicle,
                DailyCheck.Stav.ZAVAD
        );
    }

    public List<DailyCheck> findRecentDefectsByCompany(String companyKey) {
        return dailyCheckRepository.findRecentDefectsByCompany(
                DailyCheck.Stav.ZAVAD, companyKey, PageRequest.of(0, 10));
    }

    public List<DailyCheck> findRecentDefects() {
        return dailyCheckRepository.findRecentDefects(DailyCheck.Stav.ZAVAD, PageRequest.of(0, 10));
    }
}
