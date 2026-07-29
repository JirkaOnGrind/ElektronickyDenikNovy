package com.example.authdemo.repository;

import com.example.authdemo.model.Revision;
import com.example.authdemo.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface RevisionRepository extends JpaRepository<Revision, Long> {
    List<Revision> findByVehicleAndRevisionDateBetweenOrderByRevisionDateDescIdDesc(
            Vehicle vehicle,
            LocalDate startDate,
            LocalDate endDate
    );

    List<Revision> findByVehicleAndResultOrderByRevisionDateDescIdDesc(
            Vehicle vehicle,
            Revision.RevisionResult result
    );
}
