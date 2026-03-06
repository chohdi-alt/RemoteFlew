package tn.pi.remoteflowapplication.application.port.out;

import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TeleworkRequestRepository {

    TeleworkRequest save(TeleworkRequest request);

    Optional<TeleworkRequest> findById(Long id);

    Optional<TeleworkRequest> findByProcessInstanceId(String processInstanceId);

    List<TeleworkRequest> findByEmployeeId(String employeeId);

    List<TeleworkRequest> findByEmployeeIdAndWeek(
            String employeeId,
            LocalDate weekStart,
            LocalDate weekEnd);

    List<TeleworkRequest> findAll();

    void deleteById(Long id);

    void deleteAll();
}
