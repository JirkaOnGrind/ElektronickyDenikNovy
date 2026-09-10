package com.example.authdemo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "vehicles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private VehicleCategory category;

    @Column(name = "brand", nullable = false)
    private String brand;

    @Column(name = "type")
    private String type;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "serial_number", unique = true)
    private String serialNumber;

    @Column(name = "capacity")
    private Double capacity;

    @Column(name = "workplace")
    private String workplace;

    @Column(name = "company_key")
    private String companyKey;

    @Column(name = "deleted_at", columnDefinition = "DATETIME")
    private LocalDateTime deletedAt;

    // --- 1. WHITELIST (Povolené zobrazení) ---
    // Stores ONLY users who ARE allowed to see the vehicle
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "vehicle_allowed_users",
            joinColumns = @JoinColumn(name = "vehicle_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<User> allowedUsers = new HashSet<>();

    // --- 2. VEHICLE ADMINS (Správci/Vývojáři) ---
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "vehicle_admins",
            joinColumns = @JoinColumn(name = "vehicle_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<User> vehicleAdmins = new HashSet<>();

    // --- 3. MAINTENANCE PERMISSION (Údržba) ---
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "vehicle_maintenance_users",
            joinColumns = @JoinColumn(name = "vehicle_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<User> maintenanceUsers = new HashSet<>();

    // --- 4. CASCADE DELETE (Smazání kontrol při smazání vozidla) ---
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DailyCheck> dailyChecks = new ArrayList<>();

    // --- HELPER METHODS ---

    // Visibility Logic
    public void allowUser(User user) {
        this.allowedUsers.add(user);
    }

    public void removeUserAccess(User user) {
        this.allowedUsers.remove(user);
        // If they can't see it, they can't be admin either
        this.vehicleAdmins.remove(user);
        this.maintenanceUsers.remove(user);
    }

    // Vehicle Admin Logic
    public void addVehicleAdmin(User user) {
        this.vehicleAdmins.add(user);
        // A vehicle admin MUST be allowed to see the vehicle
        this.allowedUsers.add(user);
        this.maintenanceUsers.add(user);
    }

    public void removeVehicleAdmin(User user) {
        this.vehicleAdmins.remove(user);
    }

    public void addMaintenanceUser(User user) {
        this.maintenanceUsers.add(user);
        this.allowedUsers.add(user);
    }

    public void removeMaintenanceUser(User user) {
        this.maintenanceUsers.remove(user);
    }

    public String getDisplayName() {
        String displayName = joinNonEmpty(brand, type);
        return displayName.isBlank() ? "Bez názvu stroje" : displayName;
    }

    public String getDisplayNameWithSerial() {
        String displayName = getDisplayName();
        String cleanSerialNumber = cleanText(serialNumber);
        if (cleanSerialNumber == null) {
            return displayName;
        }
        return displayName + " (" + cleanSerialNumber + ")";
    }

    public String getSafeRegistrationNumber() {
        return cleanText(registrationNumber);
    }

    public static String cleanText(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty() || "null".equalsIgnoreCase(trimmedValue)) {
            return null;
        }

        return trimmedValue;
    }

    private String joinNonEmpty(String... values) {
        return Arrays.stream(values)
                .map(Vehicle::cleanText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }


    // --- ENUMS ---
    public enum VehicleCategory {
        JERABY("Jeřáby"),
        STAVEBNI_STROJE("Stavební stroje"),
        MOTOROVE_VYSOKOZDVIZNE_VOZIKY("Motorové vysokozdvižné vozíky");

        private final String displayName;

        VehicleCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
