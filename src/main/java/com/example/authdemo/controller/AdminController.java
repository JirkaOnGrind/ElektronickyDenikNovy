package com.example.authdemo.controller;

import com.example.authdemo.model.Company;
import com.example.authdemo.model.DailyCheck;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.UserRepository;
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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'SUPER_ADMIN')")
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
    private WorkplaceService workplaceService;
    @Autowired
    private DailyCheckService dailyCheckService;
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
        model.addAttribute("defectiveChecks", dailyCheckService.findRecentDefectsByCompany(loggedUser.getKey()).stream()
                .filter(check -> !loggedUser.getDismissedDefects().contains(check))
                .toList());

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

    @PostMapping("/defects/{id}/dismiss")
    public String dismissDefect(@PathVariable Long id,
                                @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        DailyCheck defect = dailyCheckService.getDailyCheckById(id)
                .filter(DailyCheck::hasError)
                .orElseThrow(() -> new IllegalArgumentException("Závada nebyla nalezena."));

        if (!"SUPER_ADMIN".equals(loggedUser.getRole())
                && !defect.getVehicle().getCompanyKey().equals(loggedUser.getKey())) {
            throw new AccessDeniedException("Nemáte oprávnění skrýt závadu jiné společnosti.");
        }

        loggedUser.dismissDefect(defect);
        userRepository.save(loggedUser);
        return "SUPER_ADMIN".equals(loggedUser.getRole())
                ? "redirect:/super-admin/dashboard"
                : "redirect:/admin/dashboard";
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

        TreeSet<String> userWorkplaces = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        users.stream()
                .map(User::getWorkplace)
                .map(Vehicle::cleanText)
                .filter(workplace -> workplace != null)
                .forEach(userWorkplaces::add);

        model.addAttribute("users", users);
        model.addAttribute("userWorkplaceOptions", List.copyOf(userWorkplaces));
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

        if ("SUPER_ADMIN".equals(targetUser.getRole())) {
            throw new AccessDeniedException("Účet superadmina nelze spravovat z firemní administrace.");
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
        model.addAttribute("canEditUser", canEditUser(loggedUser, targetUser));
        model.addAttribute("editableRoles", getEditableRoles(loggedUser));
        model.addAttribute("pageTitle", "Detail uživatele");

        return "userDetail";
    }

    @GetMapping("/users/{id}/edit")
    public String showUserEdit(@PathVariable Long id,
                               @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser,
                               RedirectAttributes redirectAttributes) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        User targetUser = userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();
        requireEditableUser(loggedUser, targetUser);

        redirectAttributes.addFlashAttribute("openUserEdit", true);
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/edit")
    public String updateUser(@PathVariable Long id,
                             @RequestParam String firstName,
                             @RequestParam String lastName,
                             @RequestParam String email,
                             @RequestParam(required = false) String phone,
                             @RequestParam String role,
                             @RequestParam(required = false) String workplace,
                             @RequestParam(required = false) String newWorkplace,
                             @RequestParam(required = false) String newPassword,
                             @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser,
                             RedirectAttributes redirectAttributes) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        User targetUser = userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();
        requireEditableUser(loggedUser, targetUser);

        if (!getEditableRoles(loggedUser).contains(role.toUpperCase())) {
            throw new AccessDeniedException("Nemáte oprávnění nastavit požadovanou roli.");
        }

        String result = userService.updateUser(
                id,
                firstName,
                lastName,
                email,
                phone,
                role,
                workplaceService.resolveWorkplace(
                        targetUser.getKey(),
                        workplace,
                        newWorkplace
                ),
                newPassword
        );

        if ("success".equals(result)) {
            redirectAttributes.addFlashAttribute("successMessage", "Údaje uživatele byly úspěšně upraveny.");
        } else {
            if ("email_exists".equals(result)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Uživatel s tímto e-mailem už existuje.");
            } else if ("phone_exists".equals(result)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Uživatel s tímto telefonním číslem už existuje.");
            } else if ("missing_required_fields".equals(result)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Vyplňte jméno, příjmení a e-mail.");
            } else if ("invalid_role".equals(result)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Zvolená role není platná.");
            } else if ("weak_password".equals(result)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Heslo musí mít alespoň 12 znaků.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Úpravu uživatele se nepodařilo uložit.");
            }
            redirectAttributes.addFlashAttribute("openUserEdit", true);
        }

        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{userId}/permissions")
    public String updateUserPermissions(@PathVariable Long userId,
                                        @RequestParam(required = false) List<Long> allowedVehicleIds,
                                        @RequestParam(required = false) List<Long> vehicleAdminIds,
                                        @RequestParam(required = false) List<Long> maintenanceVehicleIds,
                                        @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {

        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        User targetUser = userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow();

        if (!targetUser.getKey().equals(loggedUser.getKey())
                || !("USER".equals(targetUser.getRole()) || User.ROLE_MAINTENANCE.equals(targetUser.getRole()))) {
            throw new AccessDeniedException("Nemáte oprávnění měnit přístupy tohoto uživatele.");
        }

        if (!canEditUser(loggedUser, targetUser)) {
            throw new AccessDeniedException("Nemáte oprávnění měnit přístupy tohoto uživatele.");
        }

        if (allowedVehicleIds == null) allowedVehicleIds = new ArrayList<>();
        if (vehicleAdminIds == null) vehicleAdminIds = new ArrayList<>();
        if (maintenanceVehicleIds == null) maintenanceVehicleIds = new ArrayList<>();

        List<Vehicle> companyVehicles = vehicleRepository.findByCompanyKeyAndDeletedAtIsNull(targetUser.getKey());

        for (Vehicle vehicle : companyVehicles) {
            // 1. Vehicle Admin
            if (vehicleAdminIds.contains(vehicle.getId())) {
                vehicle.addVehicleAdmin(targetUser);
                vehicle.addMaintenanceUser(targetUser);
                if (!allowedVehicleIds.contains(vehicle.getId())) {
                    allowedVehicleIds.add(vehicle.getId());
                }
            } else {
                vehicle.removeVehicleAdmin(targetUser);
            }

            if (maintenanceVehicleIds.contains(vehicle.getId()) || vehicleAdminIds.contains(vehicle.getId())) {
                vehicle.addMaintenanceUser(targetUser);
                if (!allowedVehicleIds.contains(vehicle.getId())) allowedVehicleIds.add(vehicle.getId());
            } else {
                vehicle.removeMaintenanceUser(targetUser);
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
        return "redirect:/vehicles/list";
    }

    @PostMapping("users/delete/{id}")
    public String softDeleteUser(@PathVariable Long id,
                                 @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser,
                                 RedirectAttributes redirectAttributes) {
        User loggedUser = userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).orElseThrow();
        User targetUser = userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();

        if (!targetUser.getKey().equals(loggedUser.getKey())
                || targetUser.getId().equals(loggedUser.getId())
                || "SUPER_ADMIN".equals(targetUser.getRole())
                || "OWNER".equals(targetUser.getRole())) {
            throw new AccessDeniedException("Nemáte oprávnění smazat tohoto uživatele.");
        }

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
                           @RequestParam(required = false) String newWorkplace,
                           Principal principal,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        Optional<User> adminOptional = userService.findByEmail(principal.getName());
        if (adminOptional.isEmpty()) return "redirect:/login";
        User admin = adminOptional.get();

        // Tento endpoint vždy vytváří nový účet. ID nesmí být převzato z HTTP
        // model bindingu, jinak by podvržený parametr mohl přepsat existující účet.
        user.setId(null);
        user.setKey(admin.getKey());
        String requestedRole = user.getRole();
        user.setRole(User.ROLE_MAINTENANCE.equalsIgnoreCase(requestedRole) ? User.ROLE_MAINTENANCE : "USER");
        user.setGdprAccepted(true);
        user.setGdprAcceptedAt(LocalDateTime.now());
        user.setTermsAccepted(true);
        user.setTermsAcceptedAt(LocalDateTime.now());
        user.setVerificated(true);
        user.setVerificationKey(UUID.randomUUID().toString());
        user.setDeletedAt(null);
        user.setWorkplace(workplaceService.resolveWorkplace(
                admin.getKey(),
                user.getWorkplace(),
                newWorkplace
        ));

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
            } else if ("weak_password".equals(result)) {
                model.addAttribute("errorMessage", "Heslo musí mít alespoň 12 znaků.");
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
        User targetUser = userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();
        if (!targetUser.getKey().equals(loggedUser.getKey()) || !"USER".equals(targetUser.getRole())) {
            throw new AccessDeniedException("Nemáte oprávnění změnit roli tohoto uživatele.");
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
                                           @RequestParam(required = false) List<Long> maintenanceUserIds,
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
        if (maintenanceUserIds == null) maintenanceUserIds = new ArrayList<>();

        List<User> companyUsers = userRepository.findByKeyAndDeletedAtIsNull(loggedUser.getKey());

        for (User user : companyUsers) {
            // Přeskočíme přihlášeného uživatele (aby si sám sobě neodebral práva, pokud není ve formuláři)
            if (user.getId().equals(loggedUser.getId())) continue;

            if (vehicleAdminIds.contains(user.getId())) {
                vehicle.addVehicleAdmin(user);
                vehicle.addMaintenanceUser(user);
                if (!allowedUserIds.contains(user.getId())) allowedUserIds.add(user.getId());
            } else {
                vehicle.removeVehicleAdmin(user);
            }

            if (maintenanceUserIds.contains(user.getId()) || vehicleAdminIds.contains(user.getId())) {
                vehicle.addMaintenanceUser(user);
                if (!allowedUserIds.contains(user.getId())) allowedUserIds.add(user.getId());
            } else {
                vehicle.removeMaintenanceUser(user);
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
        if (!targetUser.getKey().equals(loggedUser.getKey()) || !"ADMIN".equals(targetUser.getRole())) {
            throw new AccessDeniedException("Nemáte oprávnění změnit roli tohoto uživatele.");
        }

        userService.changeRole(id, "USER");
        redirectAttributes.addFlashAttribute("successMessage", "Uživateli byla odebrána administrátorská práva.");
        return "redirect:/admin/usersList";
    }

    private void requireEditableUser(User loggedUser, User targetUser) {
        if (!targetUser.getKey().equals(loggedUser.getKey()) || !canEditUser(loggedUser, targetUser)) {
            throw new AccessDeniedException("Nemáte oprávnění upravovat tohoto uživatele.");
        }
    }

    private boolean canEditUser(User loggedUser, User targetUser) {
        if ("SUPER_ADMIN".equals(targetUser.getRole()) || loggedUser.getId().equals(targetUser.getId())) {
            return false;
        }

        if ("SUPER_ADMIN".equals(loggedUser.getRole())) {
            return true;
        }

        if ("OWNER".equals(loggedUser.getRole())) {
            return !"OWNER".equals(targetUser.getRole());
        }

        return "ADMIN".equals(loggedUser.getRole())
                && ("USER".equals(targetUser.getRole()) || User.ROLE_MAINTENANCE.equals(targetUser.getRole()));
    }

    private List<String> getEditableRoles(User loggedUser) {
        if ("SUPER_ADMIN".equals(loggedUser.getRole())) {
            return List.of("USER", "MAINTENANCE", "ADMIN", "OWNER");
        }
        if ("OWNER".equals(loggedUser.getRole())) {
            return List.of("USER", "MAINTENANCE", "ADMIN");
        }
        return List.of("USER", "MAINTENANCE");
    }

    @ModelAttribute
    public void addAttributes(Model model, @AuthenticationPrincipal org.springframework.security.core.userdetails.User authUser) {
        if (authUser != null) {
            userRepository.findByEmailAndDeletedAtIsNull(authUser.getUsername()).ifPresent(loggedUser -> {
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
