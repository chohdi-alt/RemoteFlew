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

    void reassignEmployeeId(String previousEmployeeId, String currentEmployeeId);

    void deleteById(Long id);

    void deleteAll();

    List<TeleworkRequest> findArchivedRequests();

    /**
     * Partial update: sets archiveNodeId directly via UPDATE query.
     * Avoids entity merge/cascade issues when called outside a transaction
     * (e.g. from afterCommit callbacks with detached entities).
     *
     * @return number of rows updated (0 or 1)
     */
    int updateArchiveNodeId(Long requestId, String archiveNodeId);
}
