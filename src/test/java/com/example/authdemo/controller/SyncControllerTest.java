package com.example.authdemo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.authdemo.dto.DailyCheckForm;
import com.example.authdemo.dto.MaintenanceForm;
import com.example.authdemo.model.DailyCheck;
import com.example.authdemo.model.MaintenanceRecord;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.service.DailyCheckService;
import com.example.authdemo.service.MaintenanceService;
import com.example.authdemo.service.RevisionService;
import com.example.authdemo.service.UserService;
import com.example.authdemo.service.VehicleService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class SyncControllerTest {
    @Mock
    private UserService userService;
    @Mock
    private VehicleService vehicleService;
    @Mock
    private DailyCheckService dailyCheckService;
    @Mock
    private MaintenanceService maintenanceService;
    @Mock
    private RevisionService revisionService;

    @InjectMocks
    private SyncController controller;

    @Test
    void anonymousSyncIsRejected() {
        ResponseEntity<?> response = controller.syncDailyCheck(validDailyCheckForm(), null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(dailyCheckService, never()).saveDailyCheckIfAbsent(any());
    }

    @Test
    void syncToInaccessibleVehicleIsRejected() {
        User user = new User();
        Vehicle vehicle = new Vehicle();
        Principal principal = () -> "operator@example.invalid";
        when(userService.findByEmail(principal.getName())).thenReturn(Optional.of(user));
        when(vehicleService.getVehicleById(42L)).thenReturn(Optional.of(vehicle));
        when(vehicleService.canAccessVehicle(user, vehicle)).thenReturn(false);

        ResponseEntity<?> response = controller.syncDailyCheck(validDailyCheckForm(), principal);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(dailyCheckService, never()).saveDailyCheckIfAbsent(any());
    }

    @Test
    void authorizedSyncIsSaved() {
        User user = new User();
        Vehicle vehicle = new Vehicle();
        Principal principal = () -> "operator@example.invalid";
        when(userService.findByEmail(principal.getName())).thenReturn(Optional.of(user));
        when(vehicleService.getVehicleById(42L)).thenReturn(Optional.of(vehicle));
        when(vehicleService.canAccessVehicle(user, vehicle)).thenReturn(true);

        ResponseEntity<?> response = controller.syncDailyCheck(validDailyCheckForm(), principal);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(dailyCheckService).saveDailyCheckIfAbsent(any(DailyCheck.class));
    }

    @Test
    void visibleOnlyUserCannotSyncMaintenance() {
        User user = new User();
        Vehicle vehicle = new Vehicle();
        Principal principal = () -> "viewer@example.invalid";
        when(userService.findByEmail(principal.getName())).thenReturn(Optional.of(user));
        when(vehicleService.getVehicleById(42L)).thenReturn(Optional.of(vehicle));
        when(vehicleService.canMaintainVehicle(user, vehicle)).thenReturn(false);

        ResponseEntity<?> response = controller.syncMaintenance(validMaintenanceForm(), principal);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(maintenanceService, never()).save(any());
    }

    @Test
    void maintenanceUserCanSyncMaintenance() {
        User user = new User();
        Vehicle vehicle = new Vehicle();
        Principal principal = () -> "maintenance@example.invalid";
        when(userService.findByEmail(principal.getName())).thenReturn(Optional.of(user));
        when(vehicleService.getVehicleById(42L)).thenReturn(Optional.of(vehicle));
        when(vehicleService.canMaintainVehicle(user, vehicle)).thenReturn(true);

        ResponseEntity<?> response = controller.syncMaintenance(validMaintenanceForm(), principal);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(maintenanceService).save(any(MaintenanceRecord.class));
    }

    private DailyCheckForm validDailyCheckForm() {
        DailyCheckForm form = new DailyCheckForm();
        form.setVehicleId(42L);
        form.setCheckDate(LocalDate.of(2026, 7, 30));
        form.setOverallResult(DailyCheck.Stav.BEZ_ZAVAD);
        return form;
    }

    private MaintenanceForm validMaintenanceForm() {
        MaintenanceForm form = new MaintenanceForm();
        form.setVehicleId(42L);
        form.setMaintenanceDate(LocalDate.of(2026, 7, 30));
        form.setResult(MaintenanceRecord.MaintenanceResult.BEZ_ZAVAD);
        return form;
    }
}
