package com.example.authdemo.service;

import com.example.authdemo.model.DailyCheck;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.DailyCheckRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyCheckServiceTest {

    @Mock
    private DailyCheckRepository dailyCheckRepository;

    @InjectMocks
    private DailyCheckService dailyCheckService;

    @Test
    void returnsEveryVehicleCheckedToday() {
        Vehicle firstVehicle = vehicle(11L);
        Vehicle secondVehicle = vehicle(22L);
        Vehicle uncheckedVehicle = vehicle(33L);

        DailyCheck firstCheck = new DailyCheck(firstVehicle, null, LocalDate.now());
        DailyCheck secondCheck = new DailyCheck(secondVehicle, null, LocalDate.now());

        when(dailyCheckRepository.findByCheckDateAndVehicleIdIn(
                LocalDate.now(), List.of(11L, 22L, 33L)))
                .thenReturn(List.of(firstCheck, secondCheck));

        assertThat(dailyCheckService.getCheckedVehicleIdsToday(
                List.of(firstVehicle, secondVehicle, uncheckedVehicle)))
                .containsExactlyInAnyOrder(11L, 22L)
                .doesNotContain(33L);
    }

    private Vehicle vehicle(Long id) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        return vehicle;
    }
}
