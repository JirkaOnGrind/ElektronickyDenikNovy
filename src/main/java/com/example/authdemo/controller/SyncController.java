package com.example.authdemo.controller;

import com.example.authdemo.dto.DailyCheckForm;
import com.example.authdemo.dto.MaintenanceForm;
import com.example.authdemo.dto.RevisionForm;
import com.example.authdemo.model.DailyCheck;
import com.example.authdemo.model.MaintenanceRecord;
import com.example.authdemo.model.Revision;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.service.DailyCheckService;
import com.example.authdemo.service.MaintenanceService;
import com.example.authdemo.service.RevisionService;
import com.example.authdemo.service.UserService;
import com.example.authdemo.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    @Autowired
    private UserService userService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private DailyCheckService dailyCheckService;

    @Autowired
    private MaintenanceService maintenanceService;

    @Autowired
    private RevisionService revisionService;

    @GetMapping("/my-vehicles")
    public ResponseEntity<List<Map<String, Object>>> getMyVehicles(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        List<Vehicle> vehicles = vehicleService.getVehiclesForCurrentUser(principal);

        List<Map<String, Object>> result = vehicles.stream().map(vehicle -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", vehicle.getId());
            map.put("name", vehicle.getDisplayName());
            map.put("spz", vehicle.getSafeRegistrationNumber());
            map.put("serialNumber", vehicle.getSerialNumber());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/daily-check")
    public ResponseEntity<?> syncDailyCheck(@RequestBody DailyCheckForm form, Principal principal) {
        return processSync(principal, form.getVehicleId(), (user, vehicle) -> {
            DailyCheck check = new DailyCheck();
            check.setCheckDate(form.getCheckDate());
            check.setOverallResult(form.getOverallResult());
            check.setDefectsDescription(form.getDefectsDescription());
            check.setUser(user);
            check.setVehicle(vehicle);
            dailyCheckService.saveDailyCheck(check);
        });
    }

    @PostMapping("/maintenance")
    public ResponseEntity<?> syncMaintenance(@RequestBody MaintenanceForm form, Principal principal) {
        return processSync(principal, form.getVehicleId(), (user, vehicle) -> {
            MaintenanceRecord record = new MaintenanceRecord();
            record.setMaintenanceDate(form.getMaintenanceDate());
            record.setResult(form.getResult());
            record.setDescription(form.getDescription());
            record.setNextRevisionDate(form.getNextRevisionDate());
            record.setUser(user);
            record.setVehicle(vehicle);
            maintenanceService.save(record);
        });
    }

    @PostMapping("/revision")
    public ResponseEntity<?> syncRevision(@RequestBody RevisionForm form, Principal principal) {
        return processSync(principal, form.getVehicleId(), (user, vehicle) -> {
            Revision revision = new Revision();
            revision.setRevisionDate(form.getRevisionDate());
            revision.setFrequency(form.getFrequency());
            revision.setResult(form.getResult());
            revision.setDescription(form.getDescription());
            revision.setUser(user);
            revision.setVehicle(vehicle);
            revisionService.save(revision);
        });
    }

    private ResponseEntity<?> processSync(Principal principal, Long vehicleId, SyncAction action) {
        Optional<User> userOpt = userService.findByEmail(principal.getName());
        Optional<Vehicle> vehicleOpt = vehicleService.getVehicleById(vehicleId);

        if (userOpt.isEmpty() || vehicleOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User or Vehicle not found");
        }

        try {
            action.execute(userOpt.get(), vehicleOpt.get());
            return ResponseEntity.ok("Synced");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @FunctionalInterface
    interface SyncAction {
        void execute(User user, Vehicle vehicle);
    }
}
