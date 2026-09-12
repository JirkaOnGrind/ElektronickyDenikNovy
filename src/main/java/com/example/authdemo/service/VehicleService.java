package com.example.authdemo.service;

import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.UserRepository;
import com.example.authdemo.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VehicleService {
    private static final Logger log = LoggerFactory.getLogger(VehicleService.class);

    @Autowired
    private final UserService userService;

    @Autowired
    private final VehicleRepository vehicleRepository;

    @Autowired
    private final UserRepository userRepository;

    public boolean registerVehicle(Vehicle vehicle) {
        vehicle.setBrand(Vehicle.cleanText(vehicle.getBrand()));
        vehicle.setType(Vehicle.cleanText(vehicle.getType()));
        vehicle.setSerialNumber(Vehicle.cleanText(vehicle.getSerialNumber()));
        vehicle.setRegistrationNumber(Vehicle.cleanText(vehicle.getRegistrationNumber()));
        vehicle.setWorkplace(Vehicle.cleanText(vehicle.getWorkplace()));

        if (vehicle.getSerialNumber() != null && vehicleRepository.existsBySerialNumberAndDeletedAtIsNull(vehicle.getSerialNumber())) {
            return false;
        }

        vehicleRepository.save(vehicle);
        log.info("Vehicle registration completed.");
        return true;
    }

    public List<Vehicle> getVehiclesForCurrentUser(Principal principal) {
        String email = principal.getName();
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("ADMIN".equalsIgnoreCase(user.getRole())
                || "OWNER".equalsIgnoreCase(user.getRole())
                || "SUPER_ADMIN".equalsIgnoreCase(user.getRole())) {
            return vehicleRepository.findByCompanyKeyAndDeletedAtIsNull(user.getKey());
        }

        return vehicleRepository.findVisibleVehicles(user.getKey(), user);
    }

    public Optional<Vehicle> getVehicleById(Long id) {
        return vehicleRepository.findByIdAndDeletedAtIsNull(id);
    }

    public Optional<Vehicle> getVehicleBySerialNumber(String serialNumber) {
        return vehicleRepository.findBySerialNumberAndDeletedAtIsNull(serialNumber);
    }

    public boolean canAccessVehicle(User user, Vehicle vehicle) {
        if (user == null || vehicle == null || !vehicle.getCompanyKey().equals(user.getKey())) {
            return false;
        }

        return isGlobalAdministrator(user)
                || containsUser(vehicle.getAllowedUsers(), user)
                || containsUser(vehicle.getVehicleAdmins(), user);
    }

    public boolean canManageVehicle(User user, Vehicle vehicle) {
        if (user == null || vehicle == null || !vehicle.getCompanyKey().equals(user.getKey())) {
            return false;
        }

        return isGlobalAdministrator(user) || containsUser(vehicle.getVehicleAdmins(), user);
    }

    public boolean canMaintainVehicle(User user, Vehicle vehicle) {
        if (user == null || vehicle == null || !vehicle.getCompanyKey().equals(user.getKey())) {
            return false;
        }

        return isGlobalAdministrator(user)
                || containsUser(vehicle.getMaintenanceUsers(), user)
                || containsUser(vehicle.getVehicleAdmins(), user);
    }

    public List<Vehicle> getVehiclesForUser(User user) {
        return vehicleRepository.findVisibleVehicles(user.getKey(), user);
    }

    public List<Vehicle> getAllVehiclesForAdmin(User admin) {
        return vehicleRepository.findByCompanyKeyAndDeletedAtIsNull(admin.getKey());
    }

    public void hideVehicleFromUser(Long vehicleId, Long userIdToBan) {
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedAtIsNull(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        User userToBan = userRepository.findByIdAndDeletedAtIsNull(userIdToBan)
                .orElseThrow(() -> new RuntimeException("User not found"));

        vehicle.removeUserAccess(userToBan);
        vehicleRepository.save(vehicle);
    }

    public void restoreAccessForUser(Long vehicleId, Long userIdToAllow) {
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedAtIsNull(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        User userToAllow = userRepository.findByIdAndDeletedAtIsNull(userIdToAllow)
                .orElseThrow(() -> new RuntimeException("User not found"));

        vehicle.allowUser(userToAllow);
        vehicleRepository.save(vehicle);
    }

    public void deleteVehicle(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedAtIsNull(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        LocalDateTime deletedAt = LocalDateTime.now();
        vehicle.getAllowedUsers().clear();
        vehicle.getVehicleAdmins().clear();
        vehicle.setDeletedAt(deletedAt);
        if (vehicle.getSerialNumber() != null) {
            vehicle.setSerialNumber(buildArchivedSerialNumber(vehicle.getSerialNumber(), vehicle.getId(), deletedAt));
        }
        vehicleRepository.save(vehicle);
        log.info("Vehicle soft deletion completed.");
    }

    public String updateVehicleBySuperAdmin(Long vehicleId,
                                            String brand,
                                            String type,
                                            Vehicle.VehicleCategory category,
                                            String serialNumber,
                                            Double capacity,
                                            String registrationNumber) {
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedAtIsNull(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        return updateVehicle(
                vehicleId,
                brand,
                type,
                category,
                serialNumber,
                capacity,
                registrationNumber,
                vehicle.getWorkplace()
        );
    }

    public String updateVehicle(Long vehicleId,
                                String brand,
                                String type,
                                Vehicle.VehicleCategory category,
                                String serialNumber,
                                Double capacity,
                                String registrationNumber,
                                String workplace) {
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedAtIsNull(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        String normalizedBrand = Vehicle.cleanText(brand);
        String normalizedType = Vehicle.cleanText(type);
        String normalizedSerialNumber = Vehicle.cleanText(serialNumber);
        String normalizedRegistrationNumber = Vehicle.cleanText(registrationNumber);
        String normalizedWorkplace = Vehicle.cleanText(workplace);

        if (normalizedBrand == null || category == null) {
            return "missing_required_fields";
        }

        if (capacity != null && capacity < 0) {
            return "invalid_capacity";
        }

        if (normalizedSerialNumber != null) {
            Optional<Vehicle> vehicleWithSerial = vehicleRepository.findBySerialNumberAndDeletedAtIsNull(normalizedSerialNumber);
            if (vehicleWithSerial.isPresent() && !vehicleWithSerial.get().getId().equals(vehicle.getId())) {
                return "serial_exists";
            }
        }

        vehicle.setBrand(normalizedBrand);
        vehicle.setType(normalizedType);
        vehicle.setCategory(category);
        vehicle.setSerialNumber(normalizedSerialNumber);
        vehicle.setCapacity(capacity);
        vehicle.setRegistrationNumber(normalizedRegistrationNumber);
        vehicle.setWorkplace(normalizedWorkplace);
        vehicleRepository.save(vehicle);

        return "success";
    }

    private String buildArchivedSerialNumber(String serialNumber, Long vehicleId, LocalDateTime deletedAt) {
        String timestamp = deletedAt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "deleted-vehicle-" + vehicleId + "-" + timestamp + "-" + serialNumber.trim().replace(" ", "_");
    }

    private boolean isGlobalAdministrator(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole())
                || "OWNER".equalsIgnoreCase(user.getRole())
                || "SUPER_ADMIN".equalsIgnoreCase(user.getRole());
    }

    private boolean containsUser(java.util.Set<User> users, User expectedUser) {
        return expectedUser.getId() != null && users.stream()
                .anyMatch(user -> expectedUser.getId().equals(user.getId()));
    }
}
