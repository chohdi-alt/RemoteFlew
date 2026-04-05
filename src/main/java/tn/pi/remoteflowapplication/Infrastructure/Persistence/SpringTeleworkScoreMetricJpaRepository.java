package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.pi.remoteflowapplication.domain.entity.TeleworkScoreMetric;

public interface SpringTeleworkScoreMetricJpaRepository extends JpaRepository<TeleworkScoreMetric, Long> {
}
