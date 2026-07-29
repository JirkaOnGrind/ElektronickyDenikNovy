package com.example.authdemo.service;

import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.UserRepository;
import com.example.authdemo.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VehicleService {
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

        System.out.println("Registrace voziku: " + vehicle.getDisplayNameWithSerial());

        if (vehicle.getSerialNumber() != null && vehicleRepository.existsBySerialNumberAndDeletedAtIsNull(vehicle.getSerialNumber())) {
            System.out.println("Vyrobni cislo jiz existuje: " + vehicle.getSerialNumber());
            return false;
        }

        vehicleRepository.save(vehicle);
        System.out.println("Vozik uspesne zaregistrovan: " + vehicle.getDisplayNameWithSerial());
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
        System.out.println("Vozidlo s ID " + vehicleId + " bylo smazano.");
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

        String normalizedBrand = Vehicle.cleanText(brand);
        String normalizedType = Vehicle.cleanText(type);
        String normalizedSerialNumber = Vehicle.cleanText(serialNumber);
        String normalizedRegistrationNumber = Vehicle.cleanText(registrationNumber);

        if (normalizedBrand == null || category == null) {
            return "missing_required_fields";
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
        vehicleRepository.save(vehicle);

        return "success";
    }

    private String buildArchivedSerialNumber(String serialNumber, Long vehicleId, LocalDateTime deletedAt) {
        String timestamp = deletedAt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "deleted-vehicle-" + vehicleId + "-" + timestamp + "-" + serialNumber.trim().replace(" ", "_");
    }
}
