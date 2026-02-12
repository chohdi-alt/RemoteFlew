package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import tn.pi.remoteflowapplication.domain.entity.Notification;
import tn.pi.remoteflowapplication.application.port.out.NotificationRepository;

@Repository
public class JpaNotificationRepository implements NotificationRepository {

    private final SpringNotificationJpaRepository jpaRepository;

    public JpaNotificationRepository(SpringNotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Notification save(Notification notification) {
        return jpaRepository.save(notification);
    }
}
