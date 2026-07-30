package com.example.authdemo.service;

import com.example.authdemo.model.Company;
import com.example.authdemo.model.User;
import com.example.authdemo.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyRegistrationService {
    private final CompanyRepository companyRepository;
    private final UserService userService;

    public CompanyRegistrationService(
            CompanyRepository companyRepository,
            UserService userService) {
        this.companyRepository = companyRepository;
        this.userService = userService;
    }

    @Transactional
    public String registerOwnerAndCompany(
            User owner,
            String companyName,
            String ico,
            String address,
            String dic,
            String companyKey) {
        String normalizedCompanyName = normalizeRequired(companyName);
        String normalizedIco = normalizeRequired(ico);
        String normalizedAddress = normalizeRequired(address);
        String normalizedDic = normalizeOptional(dic);
        String normalizedKey = CompanyKeyPolicy.normalize(companyKey);

        if (normalizedCompanyName == null
                || normalizedIco == null
                || normalizedAddress == null
                || normalizedKey == null) {
            return "missing_required_fields";
        }
        if (!CompanyKeyPolicy.isAcceptable(normalizedKey)) {
            return "weak_company_key";
        }
        if (companyRepository.existsByIco(normalizedIco)) {
            return "ico_exists";
        }
        if (companyRepository.existsByCompanyNameIgnoreCase(normalizedCompanyName)) {
            return "company_name_exists";
        }
        if (companyRepository.existsByKey(normalizedKey)) {
            return "company_key_exists";
        }

        owner.setRole("OWNER");
        owner.setKey(normalizedKey);
        String userResult = userService.registerUser(owner);
        if (!"success".equals(userResult)) {
            return userResult;
        }

        Company company = new Company(
                normalizedCompanyName,
                normalizedIco,
                normalizedAddress,
                normalizedDic,
                owner.getId(),
                normalizedKey
        );
        companyRepository.saveAndFlush(company);
        return "success";
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
