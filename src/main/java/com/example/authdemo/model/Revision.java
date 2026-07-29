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
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "revisions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Revision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "revision_date", nullable = false)
    private LocalDate revisionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, columnDefinition = "VARCHAR(32)")
    private RevisionFrequency frequency;

    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private RevisionResult result;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "DATETIME")
    private LocalDateTime createdAt;

    public enum RevisionResult {
        BEZ_ZAVAD("Bez závad"),
        ZAVAD("Závada");

        private final String label;

        RevisionResult(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum RevisionFrequency {
        MESICNE_1X(1),
        MESICE_3X(3),
        MESICE_6X(6),
        MESICE_12X(12),
        MESICE_24X(24),
        MESICE_36X(36);

        private final int months;

        RevisionFrequency(int months) {
            this.months = months;
        }

        public String getDisplayName() {
            if (months == 1) {
                return "1x měsíčně";
            }
            if (months >= 2 && months <= 4) {
                return "1x " + months + " měsíce";
            }
            return "1x " + months + " měsíců";
        }

        public int getMonths() {
            return months;
        }
    }
}
