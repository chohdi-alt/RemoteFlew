package tn.pi.remoteflowapplication.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.rule.QuotaValidationRule;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaValidationRuleTest {

        @Mock
        private TeleworkRequestRepository repository;

        private QuotaValidationRule rule;

        @BeforeEach
        void setUp() {
                rule = new QuotaValidationRule(repository);
        }

        @Test
        void acceptsValidOneDayRequest() {
                when(repository.findByEmployeeIdAndWeek(eq("emp-1"), any(), any()))
                                .thenReturn(List.of());

                TeleworkRequest request = TeleworkRequest.create(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 2));

                assertDoesNotThrow(() -> rule.validate(request, false));
        }

        @Test
        void rejectsMoreThanOneDayWithoutJustificatif() {
                when(repository.findByEmployeeIdAndWeek(eq("emp-1"), any(), any()))
                                .thenReturn(List.of());

                TeleworkRequest request = TeleworkRequest.create(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 3));

                assertThrows(BusinessException.class, () -> rule.validate(request, false));
        }

        @Test
        void acceptsMoreThanOneDayWithJustificatif() {
                when(repository.findByEmployeeIdAndWeek(eq("emp-1"), any(), any()))
                                .thenReturn(List.of());

                TeleworkRequest request = TeleworkRequest.create(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 3));

                assertDoesNotThrow(() -> rule.validate(request, true));
        }

        @Test
        void aggregatesAcrossRequestsInSameWeek() {
                LocalDate monday = LocalDate.of(2026, 2, 2);

                TeleworkRequest existing = TeleworkRequest.create(
                                "emp-1",
                                monday,
                                monday);

                when(repository.findByEmployeeIdAndWeek(eq("emp-1"), any(), any()))
                                .thenReturn(List.of(existing));

                TeleworkRequest newRequest = TeleworkRequest.create(
                                "emp-1",
                                monday.plusDays(1),
                                monday.plusDays(1));

                assertThrows(BusinessException.class, () -> rule.validate(newRequest, false));
                assertDoesNotThrow(() -> rule.validate(newRequest, true));
        }
}
