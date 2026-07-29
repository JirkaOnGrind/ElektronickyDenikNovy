package com.example.authdemo.service;

import com.example.authdemo.model.Revision;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.RevisionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class RevisionService {

    @Autowired
    private RevisionRepository revisionRepository;

    public Revision save(Revision revision) {
        return revisionRepository.save(revision);
    }

    public Optional<Revision> findById(Long id) {
        return revisionRepository.findById(id);
    }

    public List<Revision> findRevisionsByVehicleAndDateRange(Vehicle vehicle, LocalDate startDate, LocalDate endDate) {
        return revisionRepository.findByVehicleAndRevisionDateBetweenOrderByRevisionDateDescIdDesc(
                vehicle,
                startDate,
                endDate
        );
    }

    public List<Revision> findDefectsByVehicle(Vehicle vehicle) {
        return revisionRepository.findByVehicleAndResultOrderByRevisionDateDescIdDesc(
                vehicle,
                Revision.RevisionResult.ZAVAD
        );
    }
}
