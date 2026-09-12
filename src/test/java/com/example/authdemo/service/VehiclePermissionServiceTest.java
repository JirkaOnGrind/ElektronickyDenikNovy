package com.example.authdemo.service;

import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.UserRepository;
import com.example.authdemo.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehiclePermissionServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private VehicleRepository vehicleRepository;

    private VehiclePermissionService service;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        service = new VehiclePermissionService(userRepository, vehicleRepository);
        vehicle = new Vehicle();
        vehicle.setId(10L);
        vehicle.setCompanyKey("company");
        vehicle.setAllowedUsers(new HashSet<>());
        vehicle.setMaintenanceUsers(new HashSet<>());
        vehicle.setVehicleAdmins(new HashSet<>());
        vehicle.setDailyChecks(new ArrayList<>());
    }

    @Test
    void managerDoesNotSeeSelfOtherManagersOrCompanyAdmins() {
        User actor = user(1L, "manager@example.cz", "USER");
        User otherManager = user(2L, "other@example.cz", "USER");
        User admin = user(3L, "admin@example.cz", "ADMIN");
        User regular = user(4L, "user@example.cz", "USER");
        User maintenance = user(5L, "maintenance@example.cz", "MAINTENANCE");
        vehicle.addVehicleAdmin(actor);
        vehicle.addVehicleAdmin(otherManager);
        stub(actor, List.of(actor, otherManager, admin, regular, maintenance));

        var data = service.getPermissionData(vehicle.getId(), actor.getEmail());

        assertThat(data.users()).containsExactly(regular, maintenance);
    }

    @Test
    void managerCannotAssignManagerRole() {
        User actor = user(1L, "manager@example.cz", "USER");
        User regular = user(2L, "user@example.cz", "USER");
        vehicle.addVehicleAdmin(actor);
        stub(actor, List.of(actor, regular));

        assertThatThrownBy(() -> service.updatePermissions(
                vehicle.getId(), actor.getEmail(), List.of(regular.getId()), List.of(), List.of(regular.getId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void managerCannotModifyProtectedUserWithForgedRequest() {
        User actor = user(1L, "manager@example.cz", "USER");
        User admin = user(2L, "admin@example.cz", "ADMIN");
        vehicle.addVehicleAdmin(actor);
        stub(actor, List.of(actor, admin));

        assertThatThrownBy(() -> service.updatePermissions(
                vehicle.getId(), actor.getEmail(), List.of(admin.getId()), List.of(), List.of()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void managerCanGrantMaintenanceWithoutChangingProtectedPermissions() {
        User actor = user(1L, "manager@example.cz", "USER");
        User otherManager = user(2L, "other@example.cz", "USER");
        User regular = user(3L, "user@example.cz", "USER");
        vehicle.addVehicleAdmin(actor);
        vehicle.addVehicleAdmin(otherManager);
        stub(actor, List.of(actor, otherManager, regular));

        service.updatePermissions(
                vehicle.getId(), actor.getEmail(), List.of(), List.of(regular.getId()), List.of());

        assertThat(vehicle.getAllowedUsers()).contains(actor, otherManager, regular);
        assertThat(vehicle.getMaintenanceUsers()).contains(actor, otherManager, regular);
        assertThat(vehicle.getVehicleAdmins()).containsExactlyInAnyOrder(actor, otherManager);
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void administratorDoesNotSeeSelfOtherAdministratorsOrSuperAdmins() {
        User admin = user(1L, "admin@example.cz", "ADMIN");
        User regular = user(2L, "user@example.cz", "USER");
        User otherAdmin = user(3L, "other-admin@example.cz", "ADMIN");
        User superAdmin = user(4L, "super@example.cz", "SUPER_ADMIN");
        stub(admin, List.of(admin, regular, otherAdmin, superAdmin));

        assertThat(service.getPermissionData(vehicle.getId(), admin.getEmail()).users())
                .containsExactly(regular);
    }

    @Test
    void administratorCanAssignManagerRole() {
        User admin = user(1L, "admin@example.cz", "ADMIN");
        User regular = user(2L, "user@example.cz", "USER");
        stub(admin, List.of(admin, regular));

        service.updatePermissions(
                vehicle.getId(), admin.getEmail(), List.of(), List.of(), List.of(regular.getId()));

        assertThat(vehicle.getVehicleAdmins()).contains(regular);
        assertThat(vehicle.getMaintenanceUsers()).contains(regular);
        assertThat(vehicle.getAllowedUsers()).contains(regular);
    }

    @Test
    void superAdministratorDoesNotSeeSelfOrOtherSuperAdmins() {
        User actor = user(1L, "super@example.cz", "SUPER_ADMIN");
        User otherSuperAdmin = user(2L, "other-super@example.cz", "SUPER_ADMIN");
        User admin = user(3L, "admin@example.cz", "ADMIN");
        User regular = user(4L, "user@example.cz", "USER");
        stub(actor, List.of(actor, otherSuperAdmin, admin, regular));

        assertThat(service.getPermissionData(vehicle.getId(), actor.getEmail()).users())
                .containsExactly(admin, regular);
    }

    @Test
    void forgedRequestCannotModifyOwnPermissions() {
        User admin = user(1L, "admin@example.cz", "ADMIN");
        stub(admin, List.of(admin));

        assertThatThrownBy(() -> service.updatePermissions(
                vehicle.getId(), admin.getEmail(), List.of(admin.getId()), List.of(), List.of()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void forgedRequestCannotModifySuperAdministrator() {
        User actor = user(1L, "super@example.cz", "SUPER_ADMIN");
        User otherSuperAdmin = user(2L, "other-super@example.cz", "SUPER_ADMIN");
        stub(actor, List.of(actor, otherSuperAdmin));

        assertThatThrownBy(() -> service.updatePermissions(
                vehicle.getId(), actor.getEmail(), List.of(otherSuperAdmin.getId()), List.of(), List.of()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void stub(User actor, List<User> companyUsers) {
        when(userRepository.findByEmailAndDeletedAtIsNull(actor.getEmail())).thenReturn(Optional.of(actor));
        when(vehicleRepository.findByIdAndDeletedAtIsNull(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(userRepository.findByKeyAndDeletedAtIsNull(vehicle.getCompanyKey())).thenReturn(companyUsers);
    }

    private User user(Long id, String email, String role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setKey("company");
        user.setFirstName("Test");
        user.setLastName(id.toString());
        return user;
    }
}
