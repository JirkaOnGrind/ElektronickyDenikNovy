package com.example.authdemo.controller;

import com.example.authdemo.model.Company;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.VehicleRepository;
import com.example.authdemo.service.CompanyService;
import com.example.authdemo.service.UserService;
import com.example.authdemo.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
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
    private VehicleRepository vehicleRepository;

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
                                  Principal principal,
                                  Model model) {

        String userEmail = principal.getName();
        User currentUser = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Uživatel s emailem " + userEmail + " nenalezen."));

        Vehicle vehicle = new Vehicle();
        vehicle.setBrand(brand);
        vehicle.setType(type);
        vehicle.setCategory(category);
        vehicle.setSerialNumber(serialNumber);
        vehicle.setCapacity(capacity);
        vehicle.setRegistrationNumber(registrationNumber);
        vehicle.setCompanyKey(currentUser.getKey());

        if (vehicleService.registerVehicle(vehicle)) {
            return "redirect:/vehicles/list";
        }

        model.addAttribute("pageTitle", "Registrace stroje");
        model.addAttribute("error", "Výrobní číslo již existuje");
        return "vehicle-register";
    }

    @GetMapping("/vehicles/list")
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

        List<Vehicle> vehicles = vehicleRepository.findDistinctActiveByAllowedUsers_IdOrVehicleAdmins_Id(user.getId(), user.getId());

        return vehicles.stream()
                .map(v -> new OfflineVehicleDto(
                        v.getId(),
                        v.getSafeRegistrationNumber(),
                        v.getDisplayName()
                ))
                .collect(Collectors.toList());
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class OfflineVehicleDto {
        private Long id;
        private String spz;
        private String name;
    }

    @ModelAttribute
    public void addCompanyName(Model model,
                               @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {
        if (authUser != null) {
            userService.findByEmail(authUser.getUsername()).ifPresent(loggedUser -> {
                companyService.findByKey(loggedUser.getKey()).ifPresent(company -> {
                    model.addAttribute("companyName", company.getCompanyName());
                });
            });
        }
    }
}
