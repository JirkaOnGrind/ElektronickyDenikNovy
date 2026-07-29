package com.example.authdemo.service;

import com.example.authdemo.model.DailyCheck;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.DailyCheckRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class DailyCheckService {

    @Autowired
    private DailyCheckRepository dailyCheckRepository;

    public DailyCheck saveDailyCheck(DailyCheck dailyCheck) {
        return dailyCheckRepository.save(dailyCheck);
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

    public Optional<DailyCheck> findLastDefect(Vehicle vehicle) {
        return dailyCheckRepository.findTopByVehicleAndOverallResultOrderByCheckDateDesc(vehicle, DailyCheck.Stav.ZAVAD);
    }

    public List<DailyCheck> findDefectsByVehicle(Vehicle vehicle) {
        return dailyCheckRepository.findByVehicleAndOverallResultOrderByCheckDateDescIdDesc(
                vehicle,
                DailyCheck.Stav.ZAVAD
        );
    }
}
