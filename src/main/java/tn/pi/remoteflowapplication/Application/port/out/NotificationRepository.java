package tn.pi.remoteflowapplication.application.port.out;

import tn.pi.remoteflowapplication.domain.entity.Notification;

public interface NotificationRepository {
    Notification save(Notification notification);
}
