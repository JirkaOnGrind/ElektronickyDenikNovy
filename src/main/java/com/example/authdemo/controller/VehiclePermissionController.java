package com.example.authdemo.controller;

import com.example.authdemo.service.VehiclePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin/vehicles")
@RequiredArgsConstructor
public class VehiclePermissionController {
    private final VehiclePermissionService vehiclePermissionService;

    @GetMapping("/{id}/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'SUPER_ADMIN') or @vehicleSecurity.isManager(authentication, #id)")
    public String users(@PathVariable Long id,
                        Model model,
                        @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        var data = vehiclePermissionService.getPermissionData(id, principal.getUsername());
        model.addAttribute("vehicle", data.vehicle());
        model.addAttribute("users", data.users());
        model.addAttribute("user", data.actor());
        model.addAttribute("vehicleManager", !isGlobalAdministrator(data.actor().getRole()));
        return "vehicleUserList";
    }

    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'SUPER_ADMIN') or @vehicleSecurity.isManager(authentication, #id)")
    public String update(@PathVariable Long id,
                         @RequestParam(required = false) List<Long> allowedUserIds,
                         @RequestParam(required = false) List<Long> maintenanceUserIds,
                         @RequestParam(required = false) List<Long> vehicleAdminIds,
                         @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        vehiclePermissionService.updatePermissions(
                id, principal.getUsername(), allowedUserIds, maintenanceUserIds, vehicleAdminIds);
        return "redirect:/admin/vehicles/" + id + "/users?success";
    }

    private boolean isGlobalAdministrator(String role) {
        return "ADMIN".equals(role) || "OWNER".equals(role) || "SUPER_ADMIN".equals(role);
    }
}
