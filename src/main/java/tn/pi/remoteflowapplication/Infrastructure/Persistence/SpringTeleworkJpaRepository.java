package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.infrastructure.persistence.projection.MonthlyCountProjection;
import tn.pi.remoteflowapplication.infrastructure.persistence.projection.StatusCountProjection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SpringTeleworkJpaRepository
                extends JpaRepository<TeleworkRequest, Long> {

        List<TeleworkRequest> findByEmployeeId(String employeeId);

        Optional<TeleworkRequest> findByProcessInstanceId(String processInstanceId);

        @Query("""
                        select r.status as status, count(r) as count
                        from TeleworkRequest r
                        group by r.status
                        """)
        List<StatusCountProjection> countByStatus();

        @Query("""
                        select r.status as status, count(r) as count
                        from TeleworkRequest r
                        where (:from is null or r.submittedAt >= :from)
                          and (:to is null or r.submittedAt < :to)
                        group by r.status
                        """)
        List<StatusCountProjection> countByStatusAndDateRange(
                        @Param("from") Instant from,
                        @Param("to") Instant to);

        @Query("""
                        select year(r.submittedAt) as year, month(r.submittedAt) as month, count(r) as count
                        from TeleworkRequest r
                        where (:from is null or r.submittedAt >= :from)
                          and (:to is null or r.submittedAt < :to)
                        group by year(r.submittedAt), month(r.submittedAt)
                        order by year(r.submittedAt), month(r.submittedAt)
                        """)
        List<MonthlyCountProjection> countGroupedByMonth(
                        @Param("from") Instant from,
                        @Param("to") Instant to);

        @Query("""
                        select count(r)
                        from TeleworkRequest r
                        where r.employeeId = :employeeId
                        """)
        long countByEmployee(@Param("employeeId") String employeeId);

        @Query("""
                        select avg(function('timestampdiff', second, r.submittedAt, r.managerDecisionAt))
                        from TeleworkRequest r
                        where r.submittedAt is not null and r.managerDecisionAt is not null
                        """)
        Double averageManagerDecisionTime();

        @Query("""
                        select avg(function('timestampdiff', second, r.managerDecisionAt, r.hrDecisionAt))
                        from TeleworkRequest r
                        where r.managerDecisionAt is not null and r.hrDecisionAt is not null
                        """)
        Double averageHrDecisionTime();

        @Query("""
                        select avg(function('timestampdiff', second, r.submittedAt, coalesce(r.approvedAt, r.rejectedAt)))
                        from TeleworkRequest r
                        where r.submittedAt is not null and (r.approvedAt is not null or r.rejectedAt is not null)
                        """)
        Double averageTotalCycleTime();

        @Query("""
                        select count(r)
                        from TeleworkRequest r
                        where r.teamId = :teamId
                        """)
        long countByTeam(@Param("teamId") Long teamId);

        @Query("""
                        select r.status as status, count(r) as count
                        from TeleworkRequest r
                        where r.employeeId = :employeeId
                          and (:from is null or r.submittedAt >= :from)
                          and (:to is null or r.submittedAt < :to)
                        group by r.status
                        """)
        List<StatusCountProjection> countByStatusAndDateRangeForEmployee(
                        @Param("employeeId") String employeeId,
                        @Param("from") Instant from,
                        @Param("to") Instant to);

        @Query("""
                        select year(r.submittedAt) as year, month(r.submittedAt) as month, count(r) as count
                        from TeleworkRequest r
                        where r.employeeId = :employeeId
                          and (:from is null or r.submittedAt >= :from)
                          and (:to is null or r.submittedAt < :to)
                        group by year(r.submittedAt), month(r.submittedAt)
                        order by year(r.submittedAt), month(r.submittedAt)
                        """)
        List<MonthlyCountProjection> countGroupedByMonthForEmployee(
                        @Param("employeeId") String employeeId,
                        @Param("from") Instant from,
                        @Param("to") Instant to);

        @Query("""
                        select r.status as status, count(r) as count
                        from TeleworkRequest r
                        where r.teamId = :teamId
                          and (:from is null or r.submittedAt >= :from)
                          and (:to is null or r.submittedAt < :to)
                        group by r.status
                        """)
        List<StatusCountProjection> countByStatusAndDateRangeForTeam(
                        @Param("teamId") Long teamId,
                        @Param("from") Instant from,
                        @Param("to") Instant to);

        @Query("""
                        select year(r.submittedAt) as year, month(r.submittedAt) as month, count(r) as count
                        from TeleworkRequest r
                        where r.teamId = :teamId
                          and (:from is null or r.submittedAt >= :from)
                          and (:to is null or r.submittedAt < :to)
                        group by year(r.submittedAt), month(r.submittedAt)
                        order by year(r.submittedAt), month(r.submittedAt)
                        """)
        List<MonthlyCountProjection> countGroupedByMonthForTeam(
                        @Param("teamId") Long teamId,
                        @Param("from") Instant from,
                        @Param("to") Instant to);

        @Query("""
                        select avg(function('timestampdiff', second, r.submittedAt, r.managerDecisionAt))
                        from TeleworkRequest r
                        where r.teamId = :teamId
                          and r.submittedAt is not null
                          and r.managerDecisionAt is not null
                        """)
        Double averageManagerDecisionTimeForTeam(@Param("teamId") Long teamId);

        List<TeleworkRequest> findByEmployeeIdOrderBySubmittedAtDesc(String employeeId, Pageable pageable);
}
