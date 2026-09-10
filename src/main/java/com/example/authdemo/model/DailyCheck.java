package com.example.authdemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "daily_checks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_result", nullable = false)
    private Stav overallResult;

    @Column(name = "defects_description", columnDefinition = "TEXT")
    private String defectsDescription;

    @Column(name = "engine_hours")
    private Double engineHours;

    @Column(name = "fueling")
    private Double fueling;

    @Column(name = "lubrication", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean lubrication = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "DATETIME")
    private LocalDateTime createdAt;

    @ManyToMany(mappedBy = "dismissedDefects")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<User> dismissedByUsers = new HashSet<>();

    public DailyCheck(Vehicle vehicle, User user) {
        this.checkDate = LocalDate.now();
        this.vehicle = vehicle;
        this.user = user;
    }

    public DailyCheck(Vehicle vehicle, User user, LocalDate date) {
        this.checkDate = date;
        this.vehicle = vehicle;
        this.user = user;
    }

    public boolean noError() {
        return overallResult == Stav.BEZ_ZAVAD;
    }

    public boolean hasError() {
        return overallResult == Stav.ZAVAD;
    }

    public enum Stav {
        BEZ_ZAVAD,
        ZAVAD
    }
}
