package com.example.authdemo.security;

import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.UserRepository;
import com.example.authdemo.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("vehicleSecurity")
@RequiredArgsConstructor
public class VehicleSecurity {
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;

    @Transactional(readOnly = true)
    public boolean isManager(Authentication authentication, Long vehicleId) {
        if (authentication == null || !authentication.isAuthenticated() || vehicleId == null) {
            return false;
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(authentication.getName()).orElse(null);
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedAtIsNull(vehicleId).orElse(null);
        return user != null
                && vehicle != null
                && vehicle.getCompanyKey().equals(user.getKey())
                && vehicle.getVehicleAdmins().stream().anyMatch(manager -> manager.getId().equals(user.getId()));
    }
}
