package com.example.authdemo.repository;

import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findBySerialNumber(String serialNumber);
    Optional<Vehicle> findBySerialNumberAndDeletedAtIsNull(String serialNumber);
    List<Vehicle> findByBrand(String brand);
    List<Vehicle> findByType(String type);
    boolean existsBySerialNumber(String serialNumber);
    boolean existsBySerialNumberAndDeletedAtIsNull(String serialNumber);
    @EntityGraph(attributePaths = {"allowedUsers", "vehicleAdmins", "maintenanceUsers"})
    Optional<Vehicle> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"allowedUsers", "vehicleAdmins", "maintenanceUsers"})
    List<Vehicle> findByCompanyKeyAndDeletedAtIsNull(String companyKey);

    // CHANGED QUERY: Only select vehicles where the user IS in the allowedUsers list
    @EntityGraph(attributePaths = {"allowedUsers", "vehicleAdmins", "maintenanceUsers"})
    @Query("SELECT v FROM Vehicle v WHERE v.deletedAt IS NULL AND v.companyKey = :companyKey AND :user MEMBER OF v.allowedUsers")
    List<Vehicle> findVisibleVehicles(@Param("companyKey") String companyKey, @Param("user") User user);

    @Query("SELECT DISTINCT v FROM Vehicle v LEFT JOIN v.allowedUsers au LEFT JOIN v.vehicleAdmins va LEFT JOIN v.maintenanceUsers mu "
            + "WHERE v.deletedAt IS NULL AND (au.id = :userId OR va.id = :userId OR mu.id = :userId)")
    List<Vehicle> findDistinctActiveByUserPermissions(@Param("userId") Long userId);
}
