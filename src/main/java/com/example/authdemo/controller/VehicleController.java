package com.example.authdemo.controller;

import com.example.authdemo.model.Company;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.VehicleRepository;
import com.example.authdemo.service.CompanyService;
import com.example.authdemo.service.DailyCheckService;
import com.example.authdemo.service.UserService;
import com.example.authdemo.service.VehicleService;
import com.example.authdemo.service.WorkplaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class VehicleController {
    @Autowired
    private UserService userService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private DailyCheckService dailyCheckService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private WorkplaceService workplaceService;

    @GetMapping("/vehicles/register")
    public String showVehicleRegistrationForm(Model model) {
        model.addAttribute("pageTitle", "Registrace stroje");
        return "vehicle-register";
    }

    @PostMapping("/vehicles/register")
    public String registerVehicle(@RequestParam String brand,
                                  @RequestParam(required = false) String type,
                                  @RequestParam Vehicle.VehicleCategory category,
                                  @RequestParam(required = false) String serialNumber,
                                  @RequestParam(required = false) Double capacity,
                                  @RequestParam(required = false) String registrationNumber,
                                  @RequestParam(required = false) String workplace,
                                  @RequestParam(required = false) String newWorkplace,
                                  Principal principal,
                                  Model model) {

        String userEmail = principal.getName();
        User currentUser = userService.findByEmail(userEmail)
                .orElseThrow(() -> new AccessDeniedException("Přihlášený uživatel nebyl nalezen."));

        Vehicle vehicle = new Vehicle();
        vehicle.setBrand(brand);
        vehicle.setType(type);
        vehicle.setCategory(category);
        vehicle.setSerialNumber(serialNumber);
        vehicle.setCapacity(capacity);
        vehicle.setRegistrationNumber(registrationNumber);
        vehicle.setWorkplace(workplaceService.resolveWorkplace(
                currentUser.getKey(),
                workplace,
                newWorkplace
        ));
        vehicle.setCompanyKey(currentUser.getKey());

        if (vehicleService.registerVehicle(vehicle)) {
            return "redirect:/vehicles/list";
        }

        model.addAttribute("pageTitle", "Registrace stroje");
        model.addAttribute("error", "Výrobní číslo již existuje");
        return "vehicle-register";
    }

    @GetMapping("/vehicles/list")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'MAINTENANCE', 'OWNER', 'SUPER_ADMIN')")
    public String showVehiclesList(Model model, Principal principal, Authentication authentication) {
        List<Vehicle> vehicles = vehicleService.getVehiclesForCurrentUser(principal);

        String pageTitle = "Všechny stroje";
        if (principal != null) {
            User user = userService.findByEmail(principal.getName()).orElse(null);
            if (user != null) {
                Optional<Company> company = companyService.findByKey(user.getKey());
                if (company.isPresent()) {
                    pageTitle = company.get().getCompanyName();
                }
            }
        }

        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("vehicles", vehicles);
        model.addAttribute("checkedVehicleIds", dailyCheckService.getCheckedVehicleIdsToday(vehicles));
        model.addAttribute("workplaceOptions", workplaceService.getCompanyWorkplaces(
                userService.findByEmail(principal.getName()).orElseThrow().getKey()
        ));
        model.addAttribute("operatorOptions", userService.findActiveUsersByCompanyKey(
                        userService.findByEmail(principal.getName()).orElseThrow().getKey()).stream()
                .filter(user -> "USER".equalsIgnoreCase(user.getRole()) || user.isMaintenance())
                .sorted(Comparator.comparing(User::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(User::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .toList());

        boolean isAdminOrOwner = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN")
                        || role.equals("ROLE_OWNER")
                        || role.equals("ROLE_SUPER_ADMIN"));

        if (isAdminOrOwner) {
            return "vehicle-list-admin";
        }

        if (vehicles.size() == 1) {
            return "redirect:/home?vehicleId=" + vehicles.get(0).getId();
        }

        return "vehicle-list";
    }

    @GetMapping("/admin/vehicle/edit/{id}")
    public String showVehicleEdit(@PathVariable Long id,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        requireCompanyAdministratorForVehicle(id, principal);
        redirectAttributes.addFlashAttribute("openVehicleEdit", true);
        return "redirect:/home?vehicleId=" + id;
    }

    @PostMapping("/admin/vehicle/edit/{id}")
    public String updateVehicle(@PathVariable Long id,
                                @RequestParam String brand,
                                @RequestParam(required = false) String type,
                                @RequestParam Vehicle.VehicleCategory category,
                                @RequestParam(required = false) String serialNumber,
                                @RequestParam(required = false) Double capacity,
                                @RequestParam(required = false) String registrationNumber,
                                @RequestParam(required = false) String workplace,
                                @RequestParam(required = false) String newWorkplace,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        Vehicle vehicle = requireCompanyAdministratorForVehicle(id, principal);
        String resolvedWorkplace = workplaceService.resolveWorkplace(
                vehicle.getCompanyKey(),
                workplace,
                newWorkplace
        );

        String result = vehicleService.updateVehicle(
                id,
                brand,
                type,
                category,
                serialNumber,
                capacity,
                registrationNumber,
                resolvedWorkplace
        );

        if ("success".equals(result)) {
            redirectAttributes.addFlashAttribute("successMessage", "Údaje stroje byly úspěšně upraveny.");
        } else {
            if ("serial_exists".equals(result)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Stroj s tímto výrobním číslem už existuje.");
            } else if ("missing_required_fields".equals(result)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Vyplňte značku stroje a kategorii.");
            } else if ("invalid_capacity".equals(result)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Nosnost musí být nezáporná hodnota v kilogramech.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Úpravu stroje se nepodařilo uložit.");
            }
            redirectAttributes.addFlashAttribute("openVehicleEdit", true);
        }

        return "redirect:/home?vehicleId=" + id;
    }

    @PostMapping("/vehicles/delete")
    public String deleteVehicle(@RequestParam("vehicleId") Long vehicleId,
                                Authentication authentication,
                                Principal principal,
                                Model model) {

        boolean isAdminOrOwner = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ADMIN")
                        || role.equals("ROLE_ADMIN")
                        || role.equals("OWNER")
                        || role.equals("ROLE_OWNER")
                        || role.equals("SUPER_ADMIN")
                        || role.equals("ROLE_SUPER_ADMIN"));

        if (!isAdminOrOwner) {
            return "redirect:/vehicles/list?error=access_denied";
        }

        String userEmail = principal.getName();
        User currentUser = userService.findByEmail(userEmail).orElseThrow();
        Optional<Vehicle> vehicleOpt = vehicleService.getVehicleById(vehicleId);

        if (vehicleOpt.isPresent()) {
            Vehicle vehicle = vehicleOpt.get();
            if (vehicle.getCompanyKey().equals(currentUser.getKey())) {
                vehicleService.deleteVehicle(vehicleId);
                return "redirect:/vehicles/list?success=deleted";
            }
        }

        return "redirect:/vehicles/list?error=not_found";
    }

    @GetMapping("/api/offline-vehicles")
    @ResponseBody
    public List<OfflineVehicleDto> getOfflineVehicles(Principal principal) {
        if (principal == null) {
            return List.of();
        }

        User user = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Uživatel nenalezen"));

        List<Vehicle> vehicles = vehicleRepository.findDistinctActiveByUserPermissions(user.getId());

        return vehicles.stream()
                .map(v -> new OfflineVehicleDto(
                        v.getId(),
                        v.getSafeRegistrationNumber(),
                        v.getDisplayName(),
                        v.getMaintenanceUsers().contains(user)
                ))
                .collect(Collectors.toList());
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class OfflineVehicleDto {
        private Long id;
        private String spz;
        private String name;
        private boolean maintenance;
    }

    private Vehicle requireCompanyAdministratorForVehicle(Long vehicleId, Principal principal) {
        User currentUser = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new AccessDeniedException("Přihlášený uživatel nebyl nalezen."));
        Vehicle vehicle = vehicleService.getVehicleById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Stroj nebyl nalezen."));

        boolean isAdministrator = "ADMIN".equalsIgnoreCase(currentUser.getRole())
                || "OWNER".equalsIgnoreCase(currentUser.getRole())
                || "SUPER_ADMIN".equalsIgnoreCase(currentUser.getRole());

        if (!isAdministrator || !vehicle.getCompanyKey().equals(currentUser.getKey())) {
            throw new AccessDeniedException("Nemáte oprávnění upravovat tento stroj.");
        }

        return vehicle;
    }

    @ModelAttribute
    public void addCompanyName(Model model,
                               @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {
        if (authUser != null) {
            userService.findByEmail(authUser.getUsername()).ifPresent(loggedUser -> {
                companyService.findByKey(loggedUser.getKey()).ifPresent(company -> {
                    model.addAttribute("companyName", company.getCompanyName());
                });
                model.addAttribute(
                        "workplaceOptions",
                        workplaceService.getCompanyWorkplaces(loggedUser.getKey())
                );
            });
        }
    }
}
