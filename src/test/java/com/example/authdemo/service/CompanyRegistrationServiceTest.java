package com.example.authdemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.authdemo.model.Company;
import com.example.authdemo.model.User;
import com.example.authdemo.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyRegistrationServiceTest {
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private UserService userService;

    private CompanyRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new CompanyRegistrationService(companyRepository, userService);
    }

    @Test
    void existingCompanyKeyCannotCreateAnotherOwner() {
        when(companyRepository.existsByKey("company-key-with-entropy")).thenReturn(true);

        String result = service.registerOwnerAndCompany(
                new User(),
                "Example s.r.o.",
                "12345678",
                "Praha",
                null,
                "company-key-with-entropy"
        );

        assertThat(result).isEqualTo("company_key_exists");
        verify(userService, never()).registerUser(any());
        verify(companyRepository, never()).saveAndFlush(any());
    }

    @Test
    void weakCompanyKeyIsRejected() {
        String result = service.registerOwnerAndCompany(
                new User(),
                "Example s.r.o.",
                "12345678",
                "Praha",
                null,
                "short"
        );

        assertThat(result).isEqualTo("weak_company_key");
        verify(userService, never()).registerUser(any());
    }

    @Test
    void validOwnerAndCompanyAreSavedTogether() {
        User owner = new User();
        when(userService.registerUser(owner)).thenAnswer(invocation -> {
            owner.setId(7L);
            return "success";
        });

        String result = service.registerOwnerAndCompany(
                owner,
                "Example s.r.o.",
                "12345678",
                "Praha",
                null,
                "company-key-with-entropy"
        );

        assertThat(result).isEqualTo("success");
        assertThat(owner.getRole()).isEqualTo("OWNER");
        verify(companyRepository).saveAndFlush(any(Company.class));
    }
}
