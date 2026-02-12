package tn.pi.remoteflowapplication.application.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.event.DomainEvent;

@Component
public class DomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public DomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishEvents(TeleworkRequest request) {
        for (DomainEvent event : request.pullDomainEvents()) {
            publisher.publishEvent(event);
        }
    }
}
