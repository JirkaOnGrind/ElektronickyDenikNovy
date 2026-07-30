package com.example.authdemo.service;

import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.UserRepository;
import com.example.authdemo.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class WorkplaceService {
    public static final String NEW_WORKPLACE_VALUE = "__NEW__";

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    public WorkplaceService(VehicleRepository vehicleRepository, UserRepository userRepository) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
    }

    public List<String> getCompanyWorkplaces(String companyKey) {
        if (Vehicle.cleanText(companyKey) == null) {
            return List.of();
        }

        Map<String, String> workplacesByNormalizedName = new LinkedHashMap<>();
        Stream.concat(
                        vehicleRepository.findByCompanyKeyAndDeletedAtIsNull(companyKey).stream()
                                .map(vehicle -> vehicle.getWorkplace()),
                        userRepository.findByKeyAndDeletedAtIsNull(companyKey).stream()
                                .map(user -> user.getWorkplace())
                )
                .map(Vehicle::cleanText)
                .filter(workplace -> workplace != null)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(workplace -> workplacesByNormalizedName.putIfAbsent(
                        workplace.toLowerCase(Locale.ROOT),
                        workplace
                ));

        return List.copyOf(workplacesByNormalizedName.values());
    }

    public String resolveWorkplace(String companyKey, String selectedWorkplace, String newWorkplace) {
        String candidate = NEW_WORKPLACE_VALUE.equals(selectedWorkplace)
                ? newWorkplace
                : selectedWorkplace;
        String cleanedCandidate = Vehicle.cleanText(candidate);

        if (cleanedCandidate == null) {
            return null;
        }

        return getCompanyWorkplaces(companyKey).stream()
                .filter(existing -> existing.equalsIgnoreCase(cleanedCandidate))
                .findFirst()
                .orElse(cleanedCandidate);
    }
}
