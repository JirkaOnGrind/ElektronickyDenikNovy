package com.example.authdemo.controller;

import com.example.authdemo.dto.RevisionForm;
import com.example.authdemo.model.Revision;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.service.CompanyService;
import com.example.authdemo.service.RevisionService;
import com.example.authdemo.service.UserService;
import com.example.authdemo.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/revision")
public class RevisionController {
    @Autowired
    private CompanyService companyService;
    @Autowired
    private RevisionService revisionService;
    @Autowired
    private VehicleService vehicleService;
    @Autowired
    private UserService userService;

    @GetMapping
    public String showRevisionForm(@RequestParam(value = "vehicleId") Long vehicleId,
                                   @RequestParam(value = "startDate", required = false) LocalDate startDate,
                                   @RequestParam(value = "endDate", required = false) LocalDate endDate,
                                   @RequestParam(value = "mode", required = false) String mode,
                                   Principal principal,
                                   Model model) {

        // Validace
        Optional<Vehicle> vehicleOpt = vehicleService.getVehicleById(vehicleId);
        if (vehicleOpt.isEmpty()) return "redirect:/vehicles/list";
        Optional<User> userOpt = userService.findByEmail(principal.getName());
        if (userOpt.isEmpty()) return "redirect:/login";

        User user = userOpt.get();
        Vehicle vehicle = vehicleOpt.get();

        companyService.findByKey(user.getKey())
                .ifPresent(company -> model.addAttribute("companyName", company.getCompanyName()));
        // ------------------------------------------------
        // Oprávnění
        boolean isGlobalAdminOrOwner = "ADMIN".equals(user.getRole()) || "OWNER".equals(user.getRole()) || "SUPER_ADMIN".equals(user.getRole());
        boolean isVehicleAdmin = vehicle.getVehicleAdmins().stream()
                .anyMatch(admin -> admin.getId().equals(user.getId()));
        boolean canViewHistory = isGlobalAdminOrOwner || isVehicleAdmin;

        model.addAttribute("canViewHistory", canViewHistory);

        // LOGIKA: Historie vs Nový záznam
        if (canViewHistory && !"create".equals(mode)) {
            // HISTORIE
            if (startDate == null) startDate = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
            if (endDate == null) endDate = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());

            List<Revision> revisions = revisionService.findRevisionsByVehicleAndDateRange(vehicle, startDate, endDate);

            model.addAttribute("vehicle", vehicle);
            model.addAttribute("revisions", revisions);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Historie revizí - " + vehicle.getBrand());
            return "revision-history";
        }

        // FORMULÁŘ
        RevisionForm form = new RevisionForm();
        form.setRevisionDate(LocalDate.now());

        model.addAttribute("revisionForm", form);
        model.addAttribute("vehicle", vehicle);
        model.addAttribute("user", user);
        model.addAttribute("frequencies", Revision.RevisionFrequency.values());
        model.addAttribute("pageTitle", "Nová revize");

        return "revision-form";
    }

    @PostMapping
    public String processRevision(@ModelAttribute RevisionForm form, Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return "redirect:/login";
        Optional<Vehicle> vehicle = vehicleService.getVehicleById(form.getVehicleId());
        if (vehicle.isEmpty()) return "redirect:/vehicles/list";

        Revision revision = new Revision();
        revision.setRevisionDate(form.getRevisionDate());
        revision.setFrequency(form.getFrequency());
        // revision.setPerformedBy(...) - ODSTRANĚNO
        revision.setResult(form.getResult());
        revision.setDescription(form.getDescription());
        revision.setUser(user.get()); // Automaticky se uloží přihlášený uživatel
        revision.setVehicle(vehicle.get());

        revisionService.save(revision);

        return "redirect:/revision/success?revisionId=" + revision.getId();
    }

    @GetMapping("/success")
    public String showSuccessPage(@RequestParam Long revisionId, Model model, Principal principal) {
        if (principal != null) {
            userService.findByEmail(principal.getName()).ifPresent(u -> model.addAttribute("user", u));
            Optional<User> userOpt = userService.findByEmail(principal.getName());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                model.addAttribute("user", user);

                // --- TOTO TI CHYBĚLO PRO HEADER ---
                companyService.findByKey(user.getKey())
                        .ifPresent(company -> model.addAttribute("companyName", company.getCompanyName()));
                // ----------------------------------
            }
        }
        Optional<Revision> revisionOpt = revisionService.findById(revisionId);
        if (revisionOpt.isPresent()) {
            Revision revision = revisionOpt.get();
            model.addAttribute("revision", revision);

            // --- PŘIDÁNO: ID vozidla pro tlačítko "Domů" ---
            model.addAttribute("vehicleId", revision.getVehicle().getId());
            // -----------------------------------------------

            model.addAttribute("pageTitle", "Revize uložena");
        }
        return "revision-success";
    }
}