package tn.pi.remoteflowapplication.domain;

import org.junit.jupiter.api.Test;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleworkRequestStateTransitionsTest {

    @Test
    void createStartsSubmitted() {
        TeleworkRequest request = TeleworkRequest.create(
                "emp-1",
                LocalDate.now(),
                LocalDate.now()
        );

        assertEquals("SUBMITTED", request.getStatus().name());
    }

    @Test
    void approveFromSubmittedMovesToApproved() {
        TeleworkRequest request = TeleworkRequest.create(
                "emp-1",
                LocalDate.now(),
                LocalDate.now()
        );

        request.approve("ok");

        assertEquals("APPROVED", request.getStatus().name());
    }

    @Test
    void rejectFromSubmittedMovesToRejected() {
        TeleworkRequest request = TeleworkRequest.create(
                "emp-1",
                LocalDate.now(),
                LocalDate.now()
        );

        request.reject("no");

        assertEquals("REJECTED", request.getStatus().name());
    }

    @Test
    void approveFromSpecialMovesToApproved() {
        TeleworkRequest request = TeleworkRequest.create(
                "emp-1",
                LocalDate.now(),
                LocalDate.now().plusDays(2)
        );
        request.markAsSpecial();

        request.approve("hr ok");

        assertEquals("APPROVED", request.getStatus().name());
    }
}
