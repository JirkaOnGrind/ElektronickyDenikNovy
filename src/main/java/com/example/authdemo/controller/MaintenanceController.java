package com.example.authdemo.controller;

import com.example.authdemo.dto.MaintenanceForm;
import com.example.authdemo.model.MaintenanceRecord;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.service.CompanyService;
import com.example.authdemo.service.MaintenanceService;
import com.example.authdemo.service.UserService;
import com.example.authdemo.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/maintenance")
public class MaintenanceController {
    @Autowired
    private CompanyService companyService;

    @Autowired
    private MaintenanceService maintenanceService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private UserService userService;

    @GetMapping({"", "/"})
    public String showMaintenanceForm(@RequestParam(value = "vehicleId") Long vehicleId,
                                      @RequestParam(value = "startDate", required = false) LocalDate startDate,
                                      @RequestParam(value = "endDate", required = false) LocalDate endDate,
                                      @RequestParam(value = "mode", required = false) String mode,
                                      Principal principal,
                                      Model model) {

        Optional<Vehicle> vehicleOpt = vehicleService.getVehicleById(vehicleId);
        if (vehicleOpt.isEmpty()) {
            return "redirect:/vehicles/list";
        }

        Optional<User> userOpt = userService.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return "redirect:/login";
        }

        User user = userOpt.get();
        Vehicle vehicle = vehicleOpt.get();

        if (!vehicleService.canMaintainVehicle(user, vehicle)) {
            throw new AccessDeniedException("Nemáte oprávnění k modulu údržby tohoto stroje.");
        }

        companyService.findByKey(user.getKey())
                .ifPresent(company -> model.addAttribute("companyName", company.getCompanyName()));

        boolean canViewHistory = vehicleService.canManageVehicle(user, vehicle);
        model.addAttribute("canViewHistory", canViewHistory);

        if (canViewHistory && !"create".equals(mode)) {
            if (startDate == null) {
                startDate = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
            }
            if (endDate == null) {
                endDate = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());
            }

            List<MaintenanceRecord> records = maintenanceService.findRecordsByVehicleAndDateRange(vehicle, startDate, endDate);

            model.addAttribute("vehicle", vehicle);
            model.addAttribute("records", records);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Historie údržby - " + vehicle.getDisplayName());

            return "maintenance-history";
        }

        MaintenanceForm form = new MaintenanceForm();
        form.setMaintenanceDate(LocalDate.now());

        model.addAttribute("maintenanceForm", form);
        model.addAttribute("vehicle", vehicle);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Nový záznam");

        return "maintenance-form";
    }

    @PostMapping
    public String processMaintenance(@ModelAttribute MaintenanceForm form, Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) {
            return "redirect:/login";
        }

        Optional<Vehicle> vehicleOpt = vehicleService.getVehicleById(form.getVehicleId());
        if (vehicleOpt.isEmpty()) {
            return "redirect:/vehicles/list?error=vehicle_not_found";
        }

        if (!vehicleService.canMaintainVehicle(user.get(), vehicleOpt.get())) {
            throw new AccessDeniedException("Nemáte oprávnění zapisovat údržbu tohoto stroje.");
        }

        MaintenanceRecord record = new MaintenanceRecord();
        record.setMaintenanceDate(form.getMaintenanceDate() != null ? form.getMaintenanceDate() : LocalDate.now());
        record.setNextRevisionDate(form.getNextRevisionDate());
        record.setResult(form.getResult());
        record.setDescription(form.getDescription());
        record.setUser(user.get());
        record.setVehicle(vehicleOpt.get());

        maintenanceService.save(record);

        return "redirect:/maintenance/success?recordId=" + record.getId();
    }

    @GetMapping("/success")
    public String showSuccessPage(@RequestParam Long recordId, Model model, Principal principal) {
        Optional<User> userOpt = principal == null
                ? Optional.empty()
                : userService.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return "redirect:/login";
        }

        Optional<MaintenanceRecord> recordOpt = maintenanceService.findById(recordId);
        if (recordOpt.isEmpty()
                || !vehicleService.canMaintainVehicle(userOpt.get(), recordOpt.get().getVehicle())) {
            throw new AccessDeniedException("Nemáte oprávnění k tomuto záznamu údržby.");
        }

        User user = userOpt.get();
        MaintenanceRecord record = recordOpt.get();
        model.addAttribute("user", user);
        companyService.findByKey(user.getKey())
                .ifPresent(company -> model.addAttribute("companyName", company.getCompanyName()));
        model.addAttribute("record", record);
        model.addAttribute("maintenance", record);
        model.addAttribute("vehicle", record.getVehicle());
        model.addAttribute("pageTitle", "Údržba uložena");

        return "maintenance-success";
    }

}
