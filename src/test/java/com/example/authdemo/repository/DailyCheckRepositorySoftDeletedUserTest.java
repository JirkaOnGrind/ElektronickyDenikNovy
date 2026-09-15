package com.example.authdemo.repository;

import com.example.authdemo.model.DailyCheck;
import com.example.authdemo.model.MaintenanceRecord;
import com.example.authdemo.model.Revision;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DailyCheckRepositorySoftDeletedUserTest {
    @Autowired
    private DailyCheckRepository dailyCheckRepository;

    @Autowired
    private MaintenanceRecordRepository maintenanceRecordRepository;

    @Autowired
    private RevisionRepository revisionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void recentDefectsKeepHistoryWhenAuthorWasSoftDeleted() {
        User author = new User("Deleted", "Author", "deleted-author@example.invalid", null,
                "encoded-password", "company-key");
        author.setVerificated(true);
        userRepository.saveAndFlush(author);

        Vehicle vehicle = new Vehicle();
        vehicle.setCategory(Vehicle.VehicleCategory.STAVEBNI_STROJE);
        vehicle.setBrand("Test vehicle");
        vehicle.setSerialNumber("soft-delete-regression-vehicle");
        vehicle.setCompanyKey("company-key");
        vehicleRepository.saveAndFlush(vehicle);

        DailyCheck defect = new DailyCheck(vehicle, author, LocalDate.now());
        defect.setOverallResult(DailyCheck.Stav.ZAVAD);
        defect.setDefectsDescription("Regression test defect");
        dailyCheckRepository.saveAndFlush(defect);

        MaintenanceRecord maintenance = new MaintenanceRecord();
        maintenance.setMaintenanceDate(LocalDate.now());
        maintenance.setVehicle(vehicle);
        maintenance.setUser(author);
        maintenance.setResult(MaintenanceRecord.MaintenanceResult.ZAVAD);
        maintenance.setDescription("Regression maintenance defect");
        maintenanceRecordRepository.saveAndFlush(maintenance);

        Revision revision = new Revision();
        revision.setRevisionDate(LocalDate.now());
        revision.setFrequency(Revision.RevisionFrequency.MESICNE_1X);
        revision.setVehicle(vehicle);
        revision.setUser(author);
        revision.setResult(Revision.RevisionResult.ZAVAD);
        revision.setDescription("Regression revision defect");
        revisionRepository.saveAndFlush(revision);

        entityManager.createNativeQuery("update users set deleted_at = :deletedAt where id = :id")
                .setParameter("deletedAt", LocalDateTime.now())
                .setParameter("id", author.getId())
                .executeUpdate();
        entityManager.clear();

        assertThat(dailyCheckRepository
                .findRecentDefectsByCompany(
                        DailyCheck.Stav.ZAVAD, "company-key", PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(found -> assertThat(found.getUser()).isNull());
        assertThat(dailyCheckRepository
                .findRecentDefects(DailyCheck.Stav.ZAVAD, PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(found -> assertThat(found.getUser()).isNull());
        assertThat(dailyCheckRepository
                .findByVehicleAndOverallResultOrderByCheckDateDescIdDesc(vehicle, DailyCheck.Stav.ZAVAD))
                .singleElement()
                .satisfies(found -> assertThat(found.getUser()).isNull());
        assertThat(maintenanceRecordRepository
                .findByVehicleAndResultOrderByMaintenanceDateDescIdDesc(
                        vehicle, MaintenanceRecord.MaintenanceResult.ZAVAD))
                .singleElement()
                .satisfies(found -> assertThat(found.getUser()).isNull());
        assertThat(revisionRepository
                .findByVehicleAndResultOrderByRevisionDateDescIdDesc(vehicle, Revision.RevisionResult.ZAVAD))
                .singleElement()
                .satisfies(found -> assertThat(found.getUser()).isNull());
    }
}
