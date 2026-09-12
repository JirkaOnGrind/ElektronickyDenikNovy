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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

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
        if (form == null || form.getVehicleId() == null || form.getOverallResult() == null) {
            return ResponseEntity.badRequest().body("Invalid sync payload");
        }
        return processSync(principal, form.getVehicleId(), false, (user, vehicle) -> {
            DailyCheck check = new DailyCheck();
            check.setCheckDate(form.getCheckDate());
            check.setOverallResult(form.getOverallResult());
            check.setDefectsDescription(form.getDefectsDescription());
            check.setUser(user);
            check.setVehicle(vehicle);
            dailyCheckService.saveDailyCheckIfAbsent(check);
        });
    }

    @PostMapping("/maintenance")
    public ResponseEntity<?> syncMaintenance(@RequestBody MaintenanceForm form, Principal principal) {
        if (form == null || form.getVehicleId() == null || form.getResult() == null) {
            return ResponseEntity.badRequest().body("Invalid sync payload");
        }
        return processSync(principal, form.getVehicleId(), true, (user, vehicle) -> {
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
        if (form == null || form.getVehicleId() == null
                || form.getResult() == null || form.getFrequency() == null) {
            return ResponseEntity.badRequest().body("Invalid sync payload");
        }
        return processSync(principal, form.getVehicleId(), true, (user, vehicle) -> {
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

    private ResponseEntity<?> processSync(Principal principal,
                                          Long vehicleId,
                                          boolean maintenancePermissionRequired,
                                          SyncAction action) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<User> userOpt = userService.findByEmail(principal.getName());
        Optional<Vehicle> vehicleOpt = vehicleService.getVehicleById(vehicleId);

        if (userOpt.isEmpty() || vehicleOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid sync target");
        }

        boolean authorized = maintenancePermissionRequired
                ? vehicleService.canMaintainVehicle(userOpt.get(), vehicleOpt.get())
                : vehicleService.canAccessVehicle(userOpt.get(), vehicleOpt.get());
        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            action.execute(userOpt.get(), vehicleOpt.get());
            return ResponseEntity.ok("Synced");
        } catch (RuntimeException ex) {
            log.error("Offline sync failed type={}", ex.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Sync failed");
        }
    }

    @FunctionalInterface
    interface SyncAction {
        void execute(User user, Vehicle vehicle);
    }
}
