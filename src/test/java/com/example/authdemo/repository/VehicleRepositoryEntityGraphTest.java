package com.example.authdemo.repository;

import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class VehicleRepositoryEntityGraphTest {
    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void vehiclePermissionCollectionsAreLoadedForRendering() {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail("permissions@example.invalid");
        user.setPassword("not-used-by-this-test");
        user.setKey("test-company");
        user.setRole("USER");
        user.setVerificationKey("test-key");
        user.setVerificated(true);
        user.setGdprAccepted(true);
        user.setGdprAcceptedAt(LocalDateTime.now());
        user.setTermsAccepted(true);
        user.setTermsAcceptedAt(LocalDateTime.now());
        user = userRepository.saveAndFlush(user);

        Vehicle vehicle = new Vehicle();
        vehicle.setBrand("Test vehicle");
        vehicle.setCategory(Vehicle.VehicleCategory.STAVEBNI_STROJE);
        vehicle.setCompanyKey("test-company");
        vehicle.allowUser(user);
        vehicle.addMaintenanceUser(user);
        vehicle.addVehicleAdmin(user);
        vehicle = vehicleRepository.saveAndFlush(vehicle);

        Long vehicleId = vehicle.getId();
        entityManager.clear();

        Vehicle loaded = vehicleRepository.findByIdAndDeletedAtIsNull(vehicleId).orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getAllowedUsers())).isTrue();
        assertThat(Hibernate.isInitialized(loaded.getMaintenanceUsers())).isTrue();
        assertThat(Hibernate.isInitialized(loaded.getVehicleAdmins())).isTrue();
        assertThat(loaded.getAllowedUsers()).extracting(User::getEmail)
                .containsExactly("permissions@example.invalid");
    }
}
