package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.pi.remoteflowapplication.domain.entity.Notification;

public interface SpringNotificationJpaRepository extends JpaRepository<Notification, Long> {
}
