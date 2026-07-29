package com.example.authdemo.controller;

import com.example.authdemo.model.Company;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.UserRepository;
import com.example.authdemo.repository.VehicleRepository;
import com.example.authdemo.service.CompanyService;
import com.example.authdemo.service.UserService;
import com.example.authdemo.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {
    @Autowired
    private VehicleService vehicleService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private CompanyService companyService; // Injektáž služby
    @Autowired
    private final VehicleRepository vehicleRepository;

    public AdminController(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    // --- DASHBOARD (Home Admin) ---
    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();

        model.addAttribute("user", loggedUser);

        // Zkusíme najít firmu podle KLÍČE, který má uživatel
        // To funguje pro OWNERa i pro běžné ADMINy (protože sdílí stejný klíč)
        Optional<Company> companyOpt = companyService.findByKey(loggedUser.getKey());

        if (companyOpt.isPresent()) {
            // Pokud firma existuje, pošleme její název
            model.addAttribute("companyName", companyOpt.get().getCompanyName());
            model.addAttribute("companyKey", companyOpt.get().getKey());
        } else {
            // Fallback, kdyby se něco pokazilo (např. uživatel bez firmy)
            model.addAttribute("companyName", "Administrace");
            model.addAttribute("companyKey", loggedUser.getKey());
        }
        if (!"ADMIN".equals(loggedUser.getRole()) && !"OWNER".equals(loggedUser.getRole()) && !"SUPER_ADMIN".equals(loggedUser.getRole())) {
            return "redirect:/";
        }
        // Title do záložky prohlížeče necháme obecný nebo taky změníme
        model.addAttribute("pageTitle", "Administrace");

        return "homeAdmin";
    }

    // --- ZMĚNA KLÍČE (POST) ---
    @PostMapping("/changeKey")
    public String changeCompanyKey(@RequestParam("newKey") String newKey,
                                   @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser,
                                   RedirectAttributes redirectAttributes) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();

        // Volání service pro změnu (Logic: Owner Only by měl být ošetřen v service nebo zde)
        String result = companyService.updateCompanyKey(loggedUser.getId(), newKey);

        if ("success".equals(result)) {
            redirectAttributes.addFlashAttribute("successMessage", "Firemní klíč byl úspěšně změněn. Všichni uživatelé byli aktualizováni.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Chyba: " + result);
        }

        return "redirect:/admin/dashboard";
    }

    @GetMapping("/usersList")
    public String usersList(Model model, @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();

        if (!"ADMIN".equals(loggedUser.getRole()) && !"OWNER".equals(loggedUser.getRole()) && !"SUPER_ADMIN".equals(loggedUser.getRole())) {
            throw new AccessDeniedException("Nemáte oprávnění přistupovat k seznamu uživatelů.");
        }

        // 1. Načíst uživatele se stejným klíčem (firmou)
        List<User> users = userRepository.findByKeyAndDeletedAtIsNull(loggedUser.getKey());

        // 2. Odebrat sebe sama ze seznamu
        users.removeIf(u -> u.getId().equals(loggedUser.getId()));

        // --- TOTO PŘIDEJ: Nikdo (ani OWNER) nesmí v seznamu vidět SUPER_ADMINa ---
        users.removeIf(u -> "SUPER_ADMIN".equals(u.getRole()));
        // -------------------------------------------------------------------------

        // 3. Specifická pravidla pro ADMINa (nevidí OWNERa a jiné ADMINy)
        if ("ADMIN".equals(loggedUser.getRole())) {
            users.removeIf(u -> "OWNER".equals(u.getRole()) || "ADMIN".equals(u.getRole()));
        }

        model.addAttribute("users", users);
        model.addAttribute("loggedUser", loggedUser);
        model.addAttribute("pageTitle", "Uživatelé");
        return "usersList";
    }

    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable Long id, Model model, @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        User targetUser = userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new RuntimeException("User not found"));

        if (!targetUser.getKey().equals(loggedUser.getKey())) {
            throw new AccessDeniedException("Nemáš oprávnění zobrazit tohoto uživatele");
        }

        if ("ADMIN".equals(loggedUser.getRole())) {
            if ("OWNER".equals(targetUser.getRole()) || "ADMIN".equals(targetUser.getRole())) {
                throw new AccessDeniedException("Nemáte oprávnění spravovat administrátory nebo vlastníka.");
            }
        }

        List<Vehicle> allVehicles = vehicleRepository.findByCompanyKeyAndDeletedAtIsNull(targetUser.getKey());

        model.addAttribute("user", targetUser);
        model.addAttribute("loggedUser", loggedUser);
        model.addAttribute("vehicles", allVehicles);
        model.addAttribute("pageTitle", "Detail uživatele");

        return "userDetail";
    }

    @PostMapping("/users/{userId}/permissions")
    public String updateUserPermissions(@PathVariable Long userId,
                                        @RequestParam(required = false) List<Long> allowedVehicleIds,
                                        @RequestParam(required = false) List<Long> vehicleAdminIds,
                                        @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {

        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        User targetUser = userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow();

        if (!targetUser.getKey().equals(loggedUser.getKey())) {
            throw new AccessDeniedException("Nemáš oprávnění.");
        }

        if (allowedVehicleIds == null) allowedVehicleIds = new ArrayList<>();
        if (vehicleAdminIds == null) vehicleAdminIds = new ArrayList<>();

        List<Vehicle> companyVehicles = vehicleRepository.findByCompanyKeyAndDeletedAtIsNull(targetUser.getKey());

        for (Vehicle vehicle : companyVehicles) {
            // 1. Vehicle Admin
            if (vehicleAdminIds.contains(vehicle.getId())) {
                vehicle.addVehicleAdmin(targetUser);
                if (!allowedVehicleIds.contains(vehicle.getId())) {
                    allowedVehicleIds.add(vehicle.getId());
                }
            } else {
                vehicle.removeVehicleAdmin(targetUser);
            }

            // 2. Visibility
            if (allowedVehicleIds.contains(vehicle.getId())) {
                vehicle.allowUser(targetUser);
            } else {
                vehicle.removeUserAccess(targetUser);
            }
            vehicleRepository.save(vehicle);
        }

        return "redirect:/admin/users/" + userId + "?success";
    }

    @GetMapping("/machines")
    public String machinesList(Model model, Principal principal) {
        List<Vehicle> vehicles = vehicleService.getVehiclesForCurrentUser(principal);
        model.addAttribute("pageTitle", "Všechny stroje");
        model.addAttribute("vehicles", vehicles);
        return "vehicle-list-admin";
    }

    @PostMapping("users/delete/{id}")
    public String softDeleteUser(@PathVariable Long id,
                                 @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser,
                                 RedirectAttributes redirectAttributes) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        User targetUser = userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();

        if ("OWNER".equals(loggedUser.getRole())|| "SUPER_ADMIN".equals(loggedUser.getRole())) {
            // Allowed
        } else if ("ADMIN".equals(loggedUser.getRole())) {
            if ("ADMIN".equals(targetUser.getRole()) || "OWNER".equals(targetUser.getRole())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Nemůžete smazat jiného administrátora nebo vlastníka.");
                return "redirect:/admin/users/" + id;
            }
        } else {
            throw new AccessDeniedException("Nemáte oprávnění mazat uživatele.");
        }

        try {
            userService.softDelete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Uživatel byl úspěšně smazán.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chyba při mazání uživatele.");
        }
        return "redirect:/admin/usersList";
    }

    @GetMapping("/users/new")
    public String usersNew(Model model) {
        model.addAttribute("pageTitle", "Přidání uživatele");
        model.addAttribute("user", new User());
        return "addUser";
    }

    @PostMapping("/users/save")
    public String saveUser(@ModelAttribute("user") User user,
                           Principal principal,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        Optional<User> adminOptional = userService.findByEmail(principal.getName());
        if (adminOptional.isEmpty()) return "redirect:/login";
        User admin = adminOptional.get();

        user.setKey(admin.getKey());
        user.setRole("USER");
        user.setGdprAccepted(true);
        user.setGdprAcceptedAt(LocalDateTime.now());
        user.setTermsAccepted(true);
        user.setTermsAcceptedAt(LocalDateTime.now());
        user.setVerificated(true);
        user.setVerificationKey(UUID.randomUUID().toString());
        user.setDeletedAt(null);

        String result = userService.registerUser(user);

        if ("success".equals(result)) {
            redirectAttributes.addFlashAttribute("successMessage", "Uživatel byl úspěšně přidán.");
            return "redirect:/admin/usersList";
        } else {
            if ("email_exists".equals(result)) {
                model.addAttribute("errorMessage", "Uživatel s tímto emailem již existuje.");
            } else if ("phone_exists".equals(result)) {
                model.addAttribute("errorMessage", "Uživatel s tímto telefonním číslem již existuje.");
            } else if ("invalid_key".equals(result)) {
                model.addAttribute("errorMessage", "Neplatný firemní klíč.");
            } else {
                model.addAttribute("errorMessage", "Nastala neznámá chyba.");
            }
            return "addUser";
        }
    }

    @PostMapping("users/promote/{id}")
    public String promoteUserToAdmin(@PathVariable Long id,
                                     @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser,
                                     RedirectAttributes redirectAttributes) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        if (!"OWNER".equals(loggedUser.getRole()) && !"SUPER_ADMIN".equals(loggedUser.getRole())) {
            throw new AccessDeniedException("Pouze vlastník (OWNER) může jmenovat administrátory.");
        }
        userService.changeRole(id, "ADMIN");
        redirectAttributes.addFlashAttribute("successMessage", "Uživatel byl povýšen na admina.");
        return "redirect:/admin/usersList";
    }

    @GetMapping("/vehicles/{vehicleId}/users")
    public String vehicleUsersList(@PathVariable Long vehicleId,
                                   Model model,
                                   @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedAtIsNull(vehicleId).orElseThrow(() -> new RuntimeException("Vehicle not found"));

        boolean isGlobalAdmin = "ADMIN".equals(loggedUser.getRole()) || "OWNER".equals(loggedUser.getRole()) || "SUPER_ADMIN".equals(loggedUser.getRole());
        boolean isVehicleAdmin = vehicle.getVehicleAdmins().stream().anyMatch(admin -> admin.getId().equals(loggedUser.getId()));

        if (!vehicle.getCompanyKey().equals(loggedUser.getKey()) || (!isGlobalAdmin && !isVehicleAdmin)) {
            throw new AccessDeniedException("Nemáte oprávnění spravovat toto vozidlo.");
        }

        List<User> companyUsers = userRepository.findByKeyAndDeletedAtIsNull(loggedUser.getKey());

        // 1. Odstraníme sebe (přihlášeného uživatele)
        companyUsers.removeIf(u -> u.getId().equals(loggedUser.getId()));

        // 2. NOVÁ ÚPRAVA: Odstraníme i ostatní ADMINy a OWNERa
        // Ti mají přístup automaticky, takže je v tomto seznamu nepotřebujeme vidět
        companyUsers.removeIf(u -> "ADMIN".equals(u.getRole()) || "OWNER".equals(u.getRole()) || "SUPER_ADMIN".equals(u.getRole()));

        model.addAttribute("vehicle", vehicle);
        model.addAttribute("users", companyUsers);
        model.addAttribute("user", loggedUser);

        return "vehicleUserList";
    }

    @PostMapping("/vehicles/{vehicleId}/permissions")
    public String updateVehiclePermissions(@PathVariable Long vehicleId,
                                           @RequestParam(required = false) List<Long> allowedUserIds,
                                           @RequestParam(required = false) List<Long> vehicleAdminIds,
                                           @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedAtIsNull(vehicleId).orElseThrow();

        boolean isGlobalAdmin = "ADMIN".equals(loggedUser.getRole()) || "OWNER".equals(loggedUser.getRole()) || "SUPER_ADMIN".equals(loggedUser.getRole());

        // Zbytek zůstává stejný (kontrola správce vozíku)
        boolean isVehicleAdmin = vehicle.getVehicleAdmins().stream().anyMatch(admin -> admin.getId().equals(loggedUser.getId()));

        if (!vehicle.getCompanyKey().equals(loggedUser.getKey()) || (!isGlobalAdmin && !isVehicleAdmin)) {
            throw new AccessDeniedException("Nemáte oprávnění spravovat toto vozidlo.");
        }

        if (allowedUserIds == null) allowedUserIds = new ArrayList<>();
        if (vehicleAdminIds == null) vehicleAdminIds = new ArrayList<>();

        List<User> companyUsers = userRepository.findByKeyAndDeletedAtIsNull(loggedUser.getKey());

        for (User user : companyUsers) {
            // Přeskočíme přihlášeného uživatele (aby si sám sobě neodebral práva, pokud není ve formuláři)
            if (user.getId().equals(loggedUser.getId())) continue;

            if (vehicleAdminIds.contains(user.getId())) {
                vehicle.addVehicleAdmin(user);
                if (!allowedUserIds.contains(user.getId())) allowedUserIds.add(user.getId());
            } else {
                vehicle.removeVehicleAdmin(user);
            }

            if (allowedUserIds.contains(user.getId())) {
                vehicle.allowUser(user);
            } else {
                vehicle.removeUserAccess(user);
            }
        }
        vehicleRepository.save(vehicle);
        return "redirect:/admin/vehicles/" + vehicleId + "/users?success";
    }

    @PostMapping("users/demote/{id}")
    public String demoteAdminToUser(@PathVariable Long id,
                                    @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser,
                                    RedirectAttributes redirectAttributes) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        User targetUser = userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();

        if (!"OWNER".equals(loggedUser.getRole()) && !"SUPER_ADMIN".equals(loggedUser.getRole())) {
            throw new AccessDeniedException("Pouze vlastník (OWNER) nebo Super Admin může odebírat oprávnění.");
        }
        if ("OWNER".equals(targetUser.getRole())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nemůžete odebrat oprávnění vlastníkovi.");
            return "redirect:/admin/users/" + id;
        }

        userService.changeRole(id, "USER");
        redirectAttributes.addFlashAttribute("successMessage", "Uživateli byla odebrána administrátorská práva.");
        return "redirect:/admin/usersList";
    }
    @ModelAttribute
    public void addAttributes(Model model, @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {
        if (authUser != null) {
            userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).ifPresent(loggedUser -> {
                companyService.findByKey(loggedUser.getKey()).ifPresent(company -> {
                    model.addAttribute("companyName", company.getCompanyName());
                });
            });
        }
    }
    // Přidejte do src/main/java/com/example/authdemo/controller/AdminController.java

    @ModelAttribute
    public void addCompanyName(Model model, @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {
        if (authUser != null) {
            userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).ifPresent(loggedUser -> {
                companyService.findByKey(loggedUser.getKey()).ifPresent(company -> {
                    model.addAttribute("companyName", company.getCompanyName());
                });
            });
        }
    }
}
