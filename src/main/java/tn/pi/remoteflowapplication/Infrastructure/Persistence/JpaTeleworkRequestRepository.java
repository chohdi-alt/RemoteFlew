package tn.pi.remoteflowapplication.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaTeleworkRequestRepository implements TeleworkRequestRepository {

    private final SpringTeleworkJpaRepository jpaRepository;
    private final EntityManager entityManager;

    public JpaTeleworkRequestRepository(
            SpringTeleworkJpaRepository jpaRepository,
            EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public TeleworkRequest save(TeleworkRequest request) {
        return jpaRepository.save(request);
    }

    @Override
    public Optional<TeleworkRequest> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<TeleworkRequest> findByProcessInstanceId(String processInstanceId) {
        return jpaRepository.findByProcessInstanceId(processInstanceId);
    }

    @Override
    public List<TeleworkRequest> findByEmployeeId(String employeeId) {
        return jpaRepository.findByEmployeeId(employeeId);
    }

    @Override
    public List<TeleworkRequest> findByEmployeeIdAndWeek(
            String employeeId,
            LocalDate weekStart,
            LocalDate weekEnd) {
        return entityManager.createQuery(
                "SELECT r FROM TeleworkRequest r " +
                        "WHERE r.employeeId = :employeeId " +
                        "AND r.startDate <= :weekEnd " +
                        "AND r.endDate >= :weekStart",
                TeleworkRequest.class)
                .setParameter("employeeId", employeeId)
                .setParameter("weekStart", weekStart)
                .setParameter("weekEnd", weekEnd)
                .getResultList();
    }

    @Override
    public List<TeleworkRequest> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void reassignEmployeeId(String previousEmployeeId, String currentEmployeeId) {
        if (previousEmployeeId == null || currentEmployeeId == null) {
            return;
        }
        String previous = previousEmployeeId.trim();
        String current = currentEmployeeId.trim();
        if (previous.isBlank() || current.isBlank() || previous.equals(current)) {
            return;
        }

        entityManager.createQuery("""
                        update TeleworkRequest r
                        set r.employeeId = :currentEmployeeId
                        where r.employeeId = :previousEmployeeId
                        """)
                .setParameter("previousEmployeeId", previous)
                .setParameter("currentEmployeeId", current)
                .executeUpdate();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
