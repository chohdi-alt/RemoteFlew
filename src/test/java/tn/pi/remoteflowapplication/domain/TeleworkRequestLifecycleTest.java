package tn.pi.remoteflowapplication.domain;

import org.junit.jupiter.api.Test;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleworkRequestLifecycleTest {

    @Test
    void createStartsSubmitted() {
        TeleworkRequest request = TeleworkRequest.create(
                "emp-1",
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 2)
        );

        assertEquals("SUBMITTED", request.getStatus().name());
    }

    @Test
    void approveFromSubmittedMovesToApproved() {
        TeleworkRequest request = TeleworkRequest.create(
                "emp-1",
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 2)
        );

        request.approve("ok");

        assertEquals("APPROVED", request.getStatus().name());
    }

    @Test
    void rejectFromSubmittedMovesToRejected() {
        TeleworkRequest request = TeleworkRequest.create(
                "emp-1",
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 2)
        );

        request.reject("no");

        assertEquals("REJECTED", request.getStatus().name());
    }
}
