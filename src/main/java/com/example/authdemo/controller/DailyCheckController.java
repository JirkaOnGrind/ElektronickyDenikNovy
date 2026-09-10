package com.example.authdemo.controller;

import com.example.authdemo.dto.DailyCheckForm;
import com.example.authdemo.model.DailyCheck;
import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.service.CompanyService;
import com.example.authdemo.service.DailyCheckService;
import com.example.authdemo.service.UserService;
import com.example.authdemo.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/daily-check")
public class DailyCheckController {
    @Autowired
    private CompanyService companyService;

    @Autowired
    private DailyCheckService dailyCheckService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private UserService userService;

    @GetMapping({"", "/form"})
    public String showDailyCheckForm(@RequestParam(value = "vehicleId") Long vehicleId,
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

        if (!vehicleService.canAccessVehicle(user, vehicle)) {
            return "redirect:/vehicles/list?error=access_denied";
        }

        companyService.findByKey(user.getKey())
                .ifPresent(company -> model.addAttribute("companyName", company.getCompanyName()));

        boolean canViewHistory = vehicleService.canManageVehicle(user, vehicle);
        boolean dailyCheckCompletedToday =
                dailyCheckService.existsDailyCheckForVehicleToday(vehicle.getId());
        model.addAttribute("canViewHistory", canViewHistory);
        model.addAttribute("dailyCheckCompletedToday", dailyCheckCompletedToday);

        if (canViewHistory && !"create".equals(mode)) {
            if (startDate == null) {
                startDate = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
            }
            if (endDate == null) {
                endDate = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());
            }

            List<DailyCheck> checks = dailyCheckService.findChecksByVehicleAndDateRange(vehicle, startDate, endDate);

            model.addAttribute("vehicle", vehicle);
            model.addAttribute("checks", checks);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Historie kontrol - " + vehicle.getDisplayName());

            return "daily-check-form-admin";
        }

        DailyCheckForm dailyCheckForm = new DailyCheckForm();
        dailyCheckForm.setCheckDate(LocalDate.now());
        model.addAttribute("dailyCheckForm", dailyCheckForm);
        model.addAttribute("vehicle", vehicle);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Nová kontrola");

        return "daily-check-form";
    }

    @PostMapping
    public String processDailyCheck(@ModelAttribute DailyCheckForm form, Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) {
            return "redirect:/login";
        }

        Optional<Vehicle> vehicleOpt = vehicleService.getVehicleById(form.getVehicleId());
        if (vehicleOpt.isEmpty()) {
            return "redirect:/vehicles/list?error=vehicle_not_found";
        }

        if (!vehicleService.canAccessVehicle(user.get(), vehicleOpt.get())) {
            return "redirect:/vehicles/list?error=access_denied";
        }

        LocalDate checkDate = form.getCheckDate() != null ? form.getCheckDate() : LocalDate.now();

        DailyCheck dailyCheck = new DailyCheck();
        dailyCheck.setCheckDate(checkDate);
        dailyCheck.setOverallResult(form.getOverallResult());
        dailyCheck.setDefectsDescription(form.getDefectsDescription());
        dailyCheck.setEngineHours(form.getEngineHours());
        dailyCheck.setFueling(form.getFueling());
        dailyCheck.setLubrication(Boolean.TRUE.equals(form.getLubrication()));
        dailyCheck.setUser(user.get());
        dailyCheck.setVehicle(vehicleOpt.get());

        Optional<DailyCheck> savedCheck = dailyCheckService.saveDailyCheckIfAbsent(dailyCheck);
        if (savedCheck.isEmpty()) {
            return redirectToAlreadyCompleted(vehicleOpt.get().getId());
        }

        return "redirect:/daily-check/success?checkId=" + savedCheck.get().getId();
    }

    @GetMapping("/vehicle/{id}")
    public String showDailyCheckFormForVehicle(@PathVariable Long id, Principal principal, Model model) {
        return showDailyCheckForm(id, null, null, "create", principal, model);
    }

    @GetMapping("/success")
    public String showSuccessPage(@RequestParam Long checkId, Model model, Principal principal) {
        Optional<User> userOpt = principal == null
                ? Optional.empty()
                : userService.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return "redirect:/login";
        }

        Optional<DailyCheck> dailyCheckOpt = dailyCheckService.getDailyCheckById(checkId);
        if (dailyCheckOpt.isEmpty()
                || !vehicleService.canAccessVehicle(userOpt.get(), dailyCheckOpt.get().getVehicle())) {
            return "redirect:/vehicles/list?error=access_denied";
        }

        User user = userOpt.get();
        DailyCheck check = dailyCheckOpt.get();
        model.addAttribute("user", user);
        companyService.findByKey(user.getKey())
                .ifPresent(company -> model.addAttribute("companyName", company.getCompanyName()));
        model.addAttribute("dailyCheck", check);
        model.addAttribute("vehicleId", check.getVehicle().getId());
        model.addAttribute("pageTitle", "Kontrola uložena");
        model.addAttribute("STAV", DailyCheck.Stav.class);

        return "daily-check-success";
    }

    private String redirectToAlreadyCompleted(Long vehicleId) {
        return "redirect:/home?vehicleId=" + vehicleId + "&dailyCheckAlreadyCompleted=true";
    }
}
