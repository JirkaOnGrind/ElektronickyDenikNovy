package com.example.authdemo.controller;

import com.example.authdemo.model.Company;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.CompanyRepository;
import com.example.authdemo.repository.UserRepository;
import com.example.authdemo.service.CompanyService;
import com.example.authdemo.service.UserService;
import com.example.authdemo.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/super-admin")
public class SuperAdminController {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private UserService userService;

    @Autowired
    private VehicleService vehicleService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {
        List<Company> companies = companyRepository.findAll();
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();

        model.addAttribute("companies", companies);
        model.addAttribute("user", loggedUser);
        model.addAttribute("pageTitle", "Super Admin - Prehled firem");

        return "superAdminDashboard";
    }

    @PostMapping("/create-company")
    public String createCompany(@RequestParam String companyName,
                                @RequestParam String ico,
                                @RequestParam String address,
                                @RequestParam(required = false) String dic,
                                @RequestParam String key,
                                @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser,
                                RedirectAttributes redirectAttributes) {

        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();

        String trimmedCompanyName = companyName == null ? "" : companyName.trim();
        String trimmedIco = ico == null ? "" : ico.trim();
        String trimmedAddress = address == null ? "" : address.trim();
        String trimmedDic = dic == null ? "" : dic.trim();
        String trimmedKey = key == null ? "" : key.trim();

        if (trimmedCompanyName.isEmpty() || trimmedIco.isEmpty() || trimmedAddress.isEmpty() || trimmedKey.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vyplnte nazev firmy, ICO, adresu a klic.");
            return "redirect:/super-admin/dashboard";
        }

        if (companyRepository.existsByIco(trimmedIco)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Firma s timto ICO uz existuje.");
            return "redirect:/super-admin/dashboard";
        }

        if (companyRepository.existsByKey(trimmedKey)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tento klic uz pouziva jina firma.");
            return "redirect:/super-admin/dashboard";
        }

        Company company = new Company(trimmedCompanyName, trimmedIco, trimmedAddress, trimmedDic, loggedUser.getId(), trimmedKey);
        companyService.registerCompany(company);

        redirectAttributes.addFlashAttribute("successMessage", "Firma byla uspesne vytvorena.");
        return "redirect:/super-admin/dashboard";
    }

    @GetMapping("/switch-company/{companyId}")
    public String switchCompany(@PathVariable Long companyId,
                                @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser,
                                RedirectAttributes redirectAttributes) {

        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        Company targetCompany = companyRepository.findById(companyId).orElse(null);

        if (targetCompany == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Firma nenalezena.");
            return "redirect:/super-admin/dashboard";
        }

        loggedUser.setKey(targetCompany.getKey());
        userRepository.save(loggedUser);

        redirectAttributes.addFlashAttribute("successMessage", "Byli jste prepnuti do firmy: " + targetCompany.getCompanyName());
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/delete-company/{companyId}")
    public String deleteCompany(@PathVariable Long companyId, RedirectAttributes redirectAttributes) {
        try {
            Optional<Company> companyOpt = companyRepository.findById(companyId);
            if (companyOpt.isPresent()) {
                companyService.deleteFullCompany(companyId);
                redirectAttributes.addFlashAttribute("successMessage", "Firma i s uživateli byla úspěšně smazána.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Firma nebyla nalezena.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chyba pri mazani: " + e.getMessage());
        }
        return "redirect:/super-admin/dashboard";
    }

    @PostMapping("/users/{userId}/update")
    public String updateUser(@PathVariable Long userId,
                             @RequestParam String firstName,
                             @RequestParam String lastName,
                             @RequestParam String email,
                             @RequestParam(required = false) String phone,
                             @RequestParam(required = false) String newPassword,
                             @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser,
                             RedirectAttributes redirectAttributes) {
        User loggedUser = requireSuperAdmin(authUser);
        User targetUser = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!targetUser.getKey().equals(loggedUser.getKey())) {
            throw new AccessDeniedException("Nemáte oprávnění upravovat tohoto uživatele.");
        }

        String result = userService.updateUserBySuperAdmin(userId, firstName, lastName, email, phone, newPassword);

        if ("success".equals(result)) {
            redirectAttributes.addFlashAttribute("successMessage", "Údaje uživatele byly úspěšně upraveny.");
        } else if ("email_exists".equals(result)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Uživatel s tímto emailem už existuje.");
            redirectAttributes.addFlashAttribute("openUserEdit", true);
        } else if ("phone_exists".equals(result)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Uživatel s tímto telefonním číslem už existuje.");
            redirectAttributes.addFlashAttribute("openUserEdit", true);
        } else if ("missing_required_fields".equals(result)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vyplňte jméno, příjmení a email.");
            redirectAttributes.addFlashAttribute("openUserEdit", true);
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Úpravu uživatele se nepodařilo uložit.");
            redirectAttributes.addFlashAttribute("openUserEdit", true);
        }

        return "redirect:/admin/users/" + userId;
    }

    @PostMapping("/vehicles/{vehicleId}/update")
    public String updateVehicle(@PathVariable Long vehicleId,
                                @RequestParam String brand,
                                @RequestParam(required = false) String type,
                                @RequestParam Vehicle.VehicleCategory category,
                                @RequestParam(required = false) String serialNumber,
                                @RequestParam(required = false) Double capacity,
                                @RequestParam(required = false) String registrationNumber,
                                @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser,
                                RedirectAttributes redirectAttributes) {
        User loggedUser = requireSuperAdmin(authUser);
        Vehicle vehicle = vehicleService.getVehicleById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (!vehicle.getCompanyKey().equals(loggedUser.getKey())) {
            throw new AccessDeniedException("Nemáte oprávnění upravovat tento stroj.");
        }

        String result = vehicleService.updateVehicleBySuperAdmin(
                vehicleId,
                brand,
                type,
                category,
                serialNumber,
                capacity,
                registrationNumber
        );

        if ("success".equals(result)) {
            redirectAttributes.addFlashAttribute("successMessage", "Údaje stroje byly úspěšně upraveny.");
        } else if ("serial_exists".equals(result)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Stroj s tímto výrobním číslem už existuje.");
            redirectAttributes.addFlashAttribute("openVehicleEdit", true);
        } else if ("missing_required_fields".equals(result)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vyplňte název stroje a kategorii.");
            redirectAttributes.addFlashAttribute("openVehicleEdit", true);
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Úpravu stroje se nepodařilo uložit.");
            redirectAttributes.addFlashAttribute("openVehicleEdit", true);
        }

        return "redirect:/home?vehicleId=" + vehicleId;
    }

    private User requireSuperAdmin(org.springframework.security.core.userdetails.User authUser) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        if (!"SUPER_ADMIN".equals(loggedUser.getRole())) {
            throw new AccessDeniedException("Pouze super admin může provádět tuto akci.");
        }
        return loggedUser;
    }
}
