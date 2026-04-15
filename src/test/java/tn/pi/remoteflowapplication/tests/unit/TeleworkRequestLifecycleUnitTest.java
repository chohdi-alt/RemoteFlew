package tn.pi.remoteflowapplication.tests.unit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class TeleworkRequestLifecycleUnitTest {

    @Test
    void shouldMoveThroughSubmittedManagerApprovedAndApprovedStates() {
        TeleworkRequest request = TeleworkRequest.create(
                "employee-a",
                LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 4, 8));

        assertEquals(RequestStatus.SUBMITTED, request.getStatus());

        request.approveByManager("manager ok");
        assertEquals(RequestStatus.MANAGER_APPROVED, request.getStatus());
        assertNotNull(request.getManagerDecisionAt());

        request.approveByHR("hr ok");
        assertEquals(RequestStatus.APPROVED, request.getStatus());
        assertNotNull(request.getHrDecisionAt());
    }

    @Test
    void shouldRejectWhenHrApprovesBeforeManager() {
        TeleworkRequest request = TeleworkRequest.create(
                "employee-b",
                LocalDate.of(2026, 4, 9),
                LocalDate.of(2026, 4, 10));

        assertThrows(IllegalStateException.class, () -> request.approveByHR("invalid transition"));
    }

    @Test
    void shouldAllowManagerRejectionFromSpecialState() {
        TeleworkRequest request = TeleworkRequest.create(
                "employee-c",
                LocalDate.of(2026, 4, 11),
                LocalDate.of(2026, 4, 12));

        request.markAsSpecial();
        assertEquals(RequestStatus.SPECIAL, request.getStatus());

        request.rejectByManager("insufficient details");
        assertEquals(RequestStatus.REJECTED, request.getStatus());
        assertNotNull(request.getManagerDecisionAt());
    }

    @Test
    void shouldRejectStartAfterEnd() {
        assertThrows(IllegalArgumentException.class, () -> TeleworkRequest.create(
                "employee-d",
                LocalDate.of(2026, 4, 15),
                LocalDate.of(2026, 4, 14)));
    }

    @Test
    void shouldBlockAnyActionAfterFinalRejection() {
        TeleworkRequest request = TeleworkRequest.create("e", LocalDate.now(), LocalDate.now().plusDays(1));
        request.rejectByManager("no");

        assertThrows(IllegalStateException.class, () -> request.approveByManager("retry"));
        assertThrows(IllegalStateException.class, () -> request.approveByHR("retry"));
    }
}
