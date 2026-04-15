package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.pi.remoteflowapplication.domain.entity.TeleworkScore;
import tn.pi.remoteflowapplication.domain.state.ScoreStatus;

import java.util.List;
import java.util.Optional;

public interface SpringTeleworkScoreJpaRepository extends JpaRepository<TeleworkScore, Long> {

    Optional<TeleworkScore> findByTeleworkRequest_Id(Long requestId);

    boolean existsByTeleworkRequest_Id(Long requestId);

    @Query("""
            select distinct s
            from TeleworkScore s
            left join fetch s.metrics m
            where s.id = :id
            """)
    Optional<TeleworkScore> findByIdWithMetrics(@Param("id") Long id);

    @Query("""
            select distinct s
            from TeleworkScore s
            left join fetch s.metrics m
            where s.teleworkRequest.id = :requestId
            """)
    Optional<TeleworkScore> findByRequestIdWithMetrics(@Param("requestId") Long requestId);

    @Query("""
            select distinct s
            from TeleworkScore s
            join fetch s.teleworkRequest r
            left join fetch s.metrics m
            where s.status = :status
              and r.teamId in :teamIds
            order by r.endDate asc
            """)
    List<TeleworkScore> findByStatusAndTeamIds(
            @Param("status") ScoreStatus status,
            @Param("teamIds") List<Long> teamIds);

    @Query("""
            select distinct s
            from TeleworkScore s
            join fetch s.teleworkRequest r
            left join fetch s.metrics m
            where (:teamId is null or r.teamId = :teamId)
              and (:employeeId is null or lower(r.employeeId) = lower(:employeeId))
              and (:managerExternalId is null or lower(s.managerExternalId) = lower(:managerExternalId))
              and (:status is null or s.status = :status)
            order by coalesce(s.reviewedAt, s.scoredAt, s.createdAt) desc
            """)
    List<TeleworkScore> searchForHr(
            @Param("teamId") Long teamId,
            @Param("employeeId") String employeeId,
            @Param("managerExternalId") String managerExternalId,
            @Param("status") ScoreStatus status);
}
