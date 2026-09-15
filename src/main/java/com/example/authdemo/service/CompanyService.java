package com.example.authdemo.service;

import com.example.authdemo.model.Company;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.CompanyRepository;
import com.example.authdemo.repository.UserRepository;
import com.example.authdemo.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class CompanyService {
    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    public boolean registerCompany(Company company) {
        Optional<Company> existingCompanyByIco = companyRepository.findByIco(company.getIco());
        if (existingCompanyByIco.isPresent()) {
            return false;
        }
        companyRepository.save(company);
        return true;
    }

    public Optional<Company> findByIco(String ico) {
        return companyRepository.findByIco(ico);
    }

    public Optional<Company> findByAdminId(Long adminId) {
        return companyRepository.findByAdmin(adminId);
    }

    public Optional<Company> findByKey(String key) {
        return companyRepository.findByKey(key);
    }

    public void deleteCompanyByUserId(Long userId) {
        try {
            Optional<Company> company = companyRepository.findByAdmin(userId);
            company.ifPresent(companyRepository::delete);
        } catch (Exception e) {
            log.error("Company cleanup failed: {}", e.getClass().getSimpleName());
        }
    }

    @Transactional
    public String updateCompanyKey(Long adminId, String newKey) {
        String normalizedKey = CompanyKeyPolicy.normalize(newKey);
        if (!CompanyKeyPolicy.isAcceptable(normalizedKey)) {
            return "Klíč musí mít alespoň " + CompanyKeyPolicy.MINIMUM_LENGTH
                    + " znaků a nesmí obsahovat mezery.";
        }

        Optional<Company> companyOpt = companyRepository.findByAdmin(adminId);
        if (companyOpt.isEmpty()) {
            return "Firma nenalezena.";
        }

        Company company = companyOpt.get();

        if (company.getKey().equals(normalizedKey)) {
            return "Novy klic je shodny s aktualnim.";
        }

        if (companyRepository.existsByKey(normalizedKey)) {
            return "Tento klic je jiz pouzivan jinou spolecnosti.";
        }

        String oldKey = company.getKey();
        company.setKey(normalizedKey);
        companyRepository.save(company);

        List<User> users = userRepository.findByKeyAndDeletedAtIsNull(oldKey);
        for (User user : users) {
            user.setKey(newKey);
        }
        userRepository.saveAll(users);

        List<Vehicle> vehicles = vehicleRepository.findByCompanyKeyAndDeletedAtIsNull(oldKey);
        for (Vehicle vehicle : vehicles) {
            vehicle.setCompanyKey(newKey);
        }
        vehicleRepository.saveAll(vehicles);

        return "success";
    }

    @Transactional
    public void deleteFullCompany(Long companyId) {
        Optional<Company> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) {
            return;
        }

        Company company = companyOpt.get();
        LocalDateTime deletedAt = LocalDateTime.now();

        List<User> users = userRepository.findByKeyAndDeletedAtIsNull(company.getKey());
        for (User user : users) {
            user.setDeletedAt(deletedAt);
            user.setEmail(buildArchivedValue(user.getEmail(), user.getId(), deletedAt, "deleted-user-email"));
            if (user.getPhone() != null) {
                user.setPhone(buildArchivedValue(user.getPhone(), user.getId(), deletedAt, "deleted-user-phone"));
            }
        }
        userRepository.saveAll(users);

        List<Vehicle> vehicles = vehicleRepository.findByCompanyKeyAndDeletedAtIsNull(company.getKey());
        for (Vehicle vehicle : vehicles) {
            vehicle.setDeletedAt(deletedAt);
            if (vehicle.getSerialNumber() != null) {
                vehicle.setSerialNumber(buildArchivedValue(vehicle.getSerialNumber(), vehicle.getId(), deletedAt, "deleted-vehicle-serial"));
            }
        }
        vehicleRepository.saveAll(vehicles);

        companyRepository.delete(company);
    }

    private String buildArchivedValue(String originalValue, Long id, LocalDateTime deletedAt, String prefix) {
        String safeOriginalValue = originalValue == null ? "empty" : originalValue.trim().replace(" ", "_");
        String timestamp = deletedAt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + "-" + id + "-" + timestamp + "-" + safeOriginalValue;
    }
}
