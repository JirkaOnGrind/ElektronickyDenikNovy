package com.example.authdemo.controller;

import com.example.authdemo.dto.VehicleDefectItem;
import com.example.authdemo.model.Company;
import com.example.authdemo.model.DailyCheck;
import com.example.authdemo.model.MaintenanceRecord;
import com.example.authdemo.model.Revision;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException; // Import pro výjimku
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Controller
public class AuthController {
    @Autowired
    private EmailService emailService;
    @Autowired
    private UserService userService;
    @Autowired
    private VehicleService vehicleService;
    @Autowired
    private CompanyService companyService;
    @Autowired
    private CompanyRegistrationService companyRegistrationService;
    @Autowired
    private DailyCheckService dailyCheckService;
    @Autowired
    private MaintenanceService maintenanceService;
    @Autowired
    private RevisionService revisionService;
    @Autowired
    private WorkplaceService workplaceService;

    // 1. KOŘEN WEBU ("/")
    @GetMapping("/")
    public String index() {
        // Zkusíme zjistit, jestli už není přihlášený
        String redirectUrl = getRedirectUrlIfLoggedIn();
        if (redirectUrl != null) {
            return redirectUrl; // Je přihlášený -> poslat na dashboard/list
        }
        return "redirect:/login"; // Není přihlášený -> poslat na login
    }

    // 2. LOGIN FORMULÁŘ ("/login")
    @GetMapping("/login")
    public String loginForm(Model model, HttpSession session,
                            @RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout) {

        // KONTROLA: Pokud už je přihlášený (např. přes Remember Me), nepouštěj ho na login formulář
        String redirectUrl = getRedirectUrlIfLoggedIn();
        if (redirectUrl != null) {
            return redirectUrl;
        }

        // --- Klasická logika pro zobrazení formuláře ---
        model.addAttribute("pageTitle", "Login");

        if (error != null) {
            model.addAttribute("error", "Neplatné přihlašovací údaje!");
        }

        if (logout != null) {
            model.addAttribute("message", "Byli jste úspěšně odhlášeni.");
        }

        return "login";
    }

    // --- POMOCNÁ METODA PRO ROZHODOVÁNÍ KAM PŘESMĚROVAT ---
    private String getRedirectUrlIfLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Pokud je uživatel přihlášený a není to "anonym"
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {

            // Projdeme jeho role a rozhodneme
            for (GrantedAuthority authority : auth.getAuthorities()) {
                String role = authority.getAuthority();

                // POZOR: Spring Security ukládá role často jako "ROLE_ADMIN", tak kontrolujeme obojí
                if (role.contains("ADMIN") || role.contains("OWNER")) {
                    return "redirect:/admin/dashboard";
                }
            }

            // Pokud není Admin ani Owner, je to běžný User
            return "redirect:/vehicles/list";
        }

        return null; // Není přihlášený
    }

    // --- REGISTER GET ---
    @GetMapping("/register")
    public String registerUserForm(Model model, HttpSession session) {
        model.addAttribute("pageTitle", "Register");
        return "registerUser";
    }

    // --- REGISTER POST ---
    @PostMapping("/auth/registerUser")
    public String registerUser(@RequestParam String firstName,
                               @RequestParam String lastName,
                               @RequestParam String email,
                               @RequestParam(required = false) String phone,
                               @RequestParam String key,
                               @RequestParam(defaultValue = "USER") String role,
                               @RequestParam String password,
                               @RequestParam String confirmPassword,
                               @RequestParam boolean terms,
                               @RequestParam boolean gdpr,
                               Model model,
                               HttpSession session) {
        // Validace hesla
        if (!password.equals(confirmPassword)) {
            model.addAttribute("pageTitle", "Register");
            model.addAttribute("error", "Hesla se neshodují");
            return "registerUser";
        }
        // Validace souhlasů
        if (!terms || !gdpr) {
            model.addAttribute("pageTitle", "Register");
            model.addAttribute("error", "Je nutné souhlasit s obchodními podmínkami a GDPR");
            return "registerUser";
        }
        User user = new User(firstName, lastName, email, phone, password, key);
        user.setRole(User.ROLE_MAINTENANCE.equalsIgnoreCase(role) ? User.ROLE_MAINTENANCE : "USER");
        String result = userService.registerUser(user);
        if ("success".equals(result)) {
            emailService.sendVerificationEmail(user);
            session.setAttribute("pendingVerificationUserId", user.getId());
            session.setAttribute("verificationAttempts", 0);
            session.setAttribute("verificationIssuedAt", System.currentTimeMillis());
            session.setMaxInactiveInterval(15 * 60);
            return "redirect:/verification";
        } else {
            model.addAttribute("pageTitle", "Register");
            if ("email_exists".equals(result)) {
                model.addAttribute("error", "Email již existuje");
            } else if ("phone_exists".equals(result)) {
                model.addAttribute("error", "Telefonní číslo již existuje");
            } else if ("invalid_key".equals(result)) {
                model.addAttribute("error", "Neplatný klíč");
            } else if ("weak_password".equals(result)) {
                model.addAttribute("error", "Heslo musí mít alespoň 12 znaků");
            } else {
                model.addAttribute("error", "Došlo k chybě při registraci");
            }
            return "registerUser";
        }
    }

    // REGISTER COMPANY GET
    @GetMapping("/register/company")
    public String registerCompanyForm(Model model, HttpSession session) {
        model.addAttribute("pageTitle", "Register");
        return "registerCompany";
    }

    // --- REGISTER COMPANY POST ---
    @PostMapping("/auth/registerCompany")
    public String registerCompany(@RequestParam String companyName,
                                  @RequestParam String ico,
                                  @RequestParam String address,
                                  @RequestParam(required = false) String dic,
                                  @RequestParam String key,
                                  @RequestParam String firstName,
                                  @RequestParam String lastName,
                                  @RequestParam String email,
                                  @RequestParam(required = false) String phone,
                                  @RequestParam String password,
                                  @RequestParam String confirmPassword,
                                  @RequestParam boolean terms,
                                  @RequestParam boolean gdpr,
                                  Model model,
                                  HttpSession session) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("pageTitle", "Registrace společnosti");
            model.addAttribute("error", "Hesla se neshodují");
            return "registerCompany";
        }
        if (!terms || !gdpr) {
            model.addAttribute("pageTitle", "Registrace společnosti");
            model.addAttribute("error", "Je nutné souhlasit s obchodními podmínkami a GDPR");
            return "registerCompany";
        }

        User user = new User(firstName, lastName, email, phone, password, key);
        String result;
        try {
            result = companyRegistrationService.registerOwnerAndCompany(
                    user,
                    companyName,
                    ico,
                    address,
                    dic,
                    key
            );
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            result = "company_conflict";
        }

        if ("success".equals(result)) {
            emailService.sendVerificationEmail(user);
            session.setAttribute("pendingVerificationUserId", user.getId());
            session.setAttribute("verificationAttempts", 0);
            session.setAttribute("verificationIssuedAt", System.currentTimeMillis());
            session.setMaxInactiveInterval(15 * 60);
            return "redirect:/verification";
        }

        model.addAttribute("pageTitle", "Registrace společnosti");
        if ("email_exists".equals(result)) {
            model.addAttribute("error", "Email již existuje");
        } else if ("phone_exists".equals(result)) {
            model.addAttribute("error", "Telefonní číslo již existuje");
        } else if ("ico_exists".equals(result)) {
            model.addAttribute("error", "IČO již existuje");
        } else if ("company_name_exists".equals(result)) {
            model.addAttribute("error", "Název společnosti již existuje");
        } else if ("company_key_exists".equals(result)) {
            model.addAttribute("error", "Tento firemní klíč již existuje");
        } else if ("weak_company_key".equals(result)) {
            model.addAttribute(
                    "error",
                    "Firemní klíč musí mít alespoň 20 znaků a nesmí obsahovat mezery"
            );
        } else if ("missing_required_fields".equals(result)) {
            model.addAttribute("error", "Vyplňte všechna povinná pole");
        } else if ("weak_password".equals(result)) {
            model.addAttribute("error", "Heslo musí mít alespoň 12 znaků");
        } else {
            model.addAttribute("error", "Registraci společnosti se nepodařilo dokončit");
        }
        return "registerCompany";
    }

    // HOME -----------------------------------------------------------
    @GetMapping("/home")
    public String showHomePage(@RequestParam(value = "vehicleId", required = false) Long vehicleId,
                               @RequestParam(value = "dailyCheckAlreadyCompleted", defaultValue = "false")
                               boolean dailyCheckAlreadyCompleted,
                               Principal principal,
                               Model model,
                               Authentication authentication) {

        User currentUser = null;
        if (principal != null) {
            String email = principal.getName();
            Optional<User> userOpt = userService.findByEmail(email);
            if (userOpt.isPresent()) {
                currentUser = userOpt.get();
                model.addAttribute("user", currentUser);

                // --- TOTO TI TAM CHYBĚLO ---
                // Musíš načíst firmu podle klíče uživatele a poslat její název
                Optional<Company> companyOpt = companyService.findByKey(currentUser.getKey());
                if (companyOpt.isPresent()) {
                    model.addAttribute("companyName", companyOpt.get().getCompanyName());
                }
                // ---------------------------
            }
        }

        if (currentUser == null) return "redirect:/login";

        model.addAttribute(
                "workplaceOptions",
                workplaceService.getCompanyWorkplaces(currentUser.getKey())
        );

        // 1. Fetch available vehicles (needed for count)
        List<Vehicle> availableVehicles = vehicleService.getVehiclesForCurrentUser(principal);
        model.addAttribute("vehicleCount", availableVehicles.size());

        // 2. Load selected vehicle & SECURITY CHECK
        Vehicle selectedVehicle = null;
        if (vehicleId != null) {
            Optional<Vehicle> vehicleOpt = vehicleService.getVehicleById(vehicleId);

            if (vehicleOpt.isPresent()) {
                Vehicle v = vehicleOpt.get();

                // --- SECURITY CHECK START ---
                // a) Kontrola firemního klíče (Základní bariéra)
                if (!v.getCompanyKey().equals(currentUser.getKey())) {
                    // Uživatel se snaží dostat na stroj cizí firmy -> Vyhodíme ho
                    return "redirect:/vehicles/list?error=access_denied";
                }

                if (!vehicleService.canAccessVehicle(currentUser, v)) {
                    return "redirect:/vehicles/list?error=access_denied";
                }
                // --- SECURITY CHECK END ---

                selectedVehicle = v;
                model.addAttribute("selectedVehicle", selectedVehicle);
            }
        }

        // 3. AUTO-SELECT if only 1 vehicle exists and none is selected
        if (selectedVehicle == null && availableVehicles.size() == 1) {
            selectedVehicle = availableVehicles.get(0);
            // Pro jistotu zde také můžeme provést check, ale getVehiclesForCurrentUser už filtruje
            model.addAttribute("selectedVehicle", selectedVehicle);
        }

        // --- LOAD DEFECT HISTORY ---
        if (selectedVehicle != null) {
            List<VehicleDefectItem> defectHistory = buildDefectHistory(selectedVehicle);
            model.addAttribute("defectHistory", defectHistory);
            model.addAttribute("canMaintainVehicle", vehicleService.canMaintainVehicle(currentUser, selectedVehicle));
            model.addAttribute(
                    "dailyCheckCompletedToday",
                    dailyCheckService.existsDailyCheckForVehicleToday(selectedVehicle.getId())
            );
        } else {
            model.addAttribute("dailyCheckCompletedToday", false);
            model.addAttribute("canMaintainVehicle", false);
        }
        model.addAttribute("dailyCheckAlreadyCompleted", dailyCheckAlreadyCompleted);


        boolean isAdminOrOwner = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ADMIN") || role.equals("ROLE_ADMIN") ||
                        role.equals("OWNER") || role.equals("ROLE_OWNER") ||
                        role.equals("SUPER_ADMIN") || role.equals("ROLE_SUPER_ADMIN"));

        boolean isVehicleAdmin = false;
        if (selectedVehicle != null) {
            isVehicleAdmin = vehicleService.canManageVehicle(currentUser, selectedVehicle);
        }

        if (isAdminOrOwner || isVehicleAdmin) {
            return "vehicleSpecificAdmin";
        }
        Optional<Company> companyOpt = companyService.findByKey(currentUser.getKey());
        model.addAttribute("pageTitle", companyOpt.get().getCompanyName());
        return "vehicleSpecificUser";
    }

    private List<VehicleDefectItem> buildDefectHistory(Vehicle vehicle) {
        List<VehicleDefectItem> items = new ArrayList<>();

        for (DailyCheck defect : dailyCheckService.findDefectsByVehicle(vehicle)) {
            items.add(new VehicleDefectItem(
                    "Denní kontrola",
                    defect.getCheckDate(),
                    defect.getCreatedAt(),
                    defect.getUser().getFirstName() + " " + defect.getUser().getLastName(),
                    defect.getDefectsDescription()
            ));
        }

        for (MaintenanceRecord defect : maintenanceService.findDefectsByVehicle(vehicle)) {
            items.add(new VehicleDefectItem(
                    "Údržba",
                    defect.getMaintenanceDate(),
                    defect.getCreatedAt(),
                    defect.getUser().getFirstName() + " " + defect.getUser().getLastName(),
                    defect.getDescription()
            ));
        }

        for (Revision defect : revisionService.findDefectsByVehicle(vehicle)) {
            items.add(new VehicleDefectItem(
                    "Revize",
                    defect.getRevisionDate(),
                    defect.getCreatedAt(),
                    defect.getUser().getFirstName() + " " + defect.getUser().getLastName(),
                    defect.getDescription()
            ));
        }

        items.sort(
                Comparator.comparing(VehicleDefectItem::eventDate, Comparator.reverseOrder())
                        .thenComparing(
                                VehicleDefectItem::createdAt,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
        );

        return items;
    }

}
