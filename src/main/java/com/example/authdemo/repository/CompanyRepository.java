package com.example.authdemo.repository;

import com.example.authdemo.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByIco(String ico);
    boolean existsByIco(String ico);
    Optional<Company> findByKey(String key);
    boolean existsByKey(String key);
    Optional<Company> findByAdmin(Long adminId);

}