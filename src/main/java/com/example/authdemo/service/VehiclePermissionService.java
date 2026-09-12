package com.example.authdemo.service;

import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.UserRepository;
import com.example.authdemo.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VehiclePermissionService {
    private static final Set<String> GLOBAL_ROLES = Set.of("ADMIN", "OWNER", "SUPER_ADMIN");
    private static final Set<String> EDITABLE_BY_MANAGER = Set.of("USER", User.ROLE_MAINTENANCE);

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;

    @Transactional(readOnly = true)
    public VehiclePermissionData getPermissionData(Long vehicleId, String email) {
        User actor = getUser(email);
        Vehicle vehicle = getVehicle(vehicleId);
        requireCanManage(actor, vehicle);

        List<User> users = userRepository.findByKeyAndDeletedAtIsNull(vehicle.getCompanyKey()).stream()
                .filter(user -> canEditTarget(actor, vehicle, user))
                .toList();
        return new VehiclePermissionData(vehicle, actor, users);
    }

    @Transactional
    public void updatePermissions(Long vehicleId,
                                  String email,
                                  List<Long> allowedUserIds,
                                  List<Long> maintenanceUserIds,
                                  List<Long> vehicleAdminIds) {
        User actor = getUser(email);
        Vehicle vehicle = getVehicle(vehicleId);
        requireCanManage(actor, vehicle);

        Set<Long> allowedIds = copyIds(allowedUserIds);
        Set<Long> maintenanceIds = copyIds(maintenanceUserIds);
        Set<Long> managerIds = copyIds(vehicleAdminIds);
        Set<Long> requestedIds = new HashSet<>(allowedIds);
        requestedIds.addAll(maintenanceIds);
        requestedIds.addAll(managerIds);

        List<User> companyUsers = userRepository.findByKeyAndDeletedAtIsNull(vehicle.getCompanyKey());
        Set<Long> companyUserIds = companyUsers.stream().map(User::getId).collect(java.util.stream.Collectors.toSet());
        if (!companyUserIds.containsAll(requestedIds)) {
            throw new AccessDeniedException("Požadavek obsahuje uživatele z jiné firmy.");
        }

        boolean protectedUserRequested = companyUsers.stream()
                .filter(user -> requestedIds.contains(user.getId()))
                .anyMatch(user -> !canEditTarget(actor, vehicle, user));
        if (protectedUserRequested) {
            throw new AccessDeniedException("Požadavek mění oprávnění chráněného uživatele.");
        }

        boolean globalAdministrator = isGlobalAdministrator(actor);
        if (!globalAdministrator) {
            if (!managerIds.isEmpty()) {
                throw new AccessDeniedException("Správce stroje nesmí přidělovat roli správce.");
            }
        }

        for (User user : companyUsers) {
            if (!canEditTarget(actor, vehicle, user)) {
                continue;
            }
            applyPermissions(vehicle, user, allowedIds, maintenanceIds, managerIds);
        }
        vehicleRepository.save(vehicle);
    }

    private void applyPermissions(Vehicle vehicle,
                                  User user,
                                  Set<Long> allowedIds,
                                  Set<Long> maintenanceIds,
                                  Set<Long> managerIds) {
        Long userId = user.getId();
        if (managerIds.contains(userId)) {
            vehicle.addVehicleAdmin(user);
        } else {
            vehicle.removeVehicleAdmin(user);
        }

        if (maintenanceIds.contains(userId) || managerIds.contains(userId)) {
            vehicle.addMaintenanceUser(user);
        } else {
            vehicle.removeMaintenanceUser(user);
        }

        if (allowedIds.contains(userId) || maintenanceIds.contains(userId) || managerIds.contains(userId)) {
            vehicle.allowUser(user);
        } else {
            vehicle.removeUserAccess(user);
        }
    }

    private boolean isEditableByManager(User actor, Vehicle vehicle, User target) {
        return EDITABLE_BY_MANAGER.contains(target.getRole())
                && vehicle.getVehicleAdmins().stream().noneMatch(manager -> manager.getId().equals(target.getId()));
    }

    private boolean canEditTarget(User actor, Vehicle vehicle, User target) {
        if (target.getId().equals(actor.getId()) || "SUPER_ADMIN".equals(target.getRole())) {
            return false;
        }
        if (!isGlobalAdministrator(actor)) {
            return isEditableByManager(actor, vehicle, target);
        }
        return !"ADMIN".equals(actor.getRole()) || !"ADMIN".equals(target.getRole());
    }

    private void requireCanManage(User actor, Vehicle vehicle) {
        boolean sameCompany = vehicle.getCompanyKey().equals(actor.getKey());
        boolean superAdmin = "SUPER_ADMIN".equals(actor.getRole());
        boolean manager = vehicle.getVehicleAdmins().stream().anyMatch(user -> user.getId().equals(actor.getId()));
        if ((!sameCompany && !superAdmin) || (!isGlobalAdministrator(actor) && !manager)) {
            throw new AccessDeniedException("Nemáte oprávnění spravovat tento stroj.");
        }
    }

    private boolean isGlobalAdministrator(User user) {
        return GLOBAL_ROLES.contains(user.getRole());
    }

    private User getUser(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new AccessDeniedException("Uživatel nebyl nalezen."));
    }

    private Vehicle getVehicle(Long vehicleId) {
        return vehicleRepository.findByIdAndDeletedAtIsNull(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Stroj nebyl nalezen."));
    }

    private Set<Long> copyIds(List<Long> ids) {
        return ids == null ? new HashSet<>() : new HashSet<>(ids);
    }

    public record VehiclePermissionData(Vehicle vehicle, User actor, List<User> users) {
    }
}
