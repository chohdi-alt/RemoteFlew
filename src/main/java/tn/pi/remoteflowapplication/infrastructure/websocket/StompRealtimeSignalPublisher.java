package tn.pi.remoteflowapplication.infrastructure.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.port.out.RealtimeSignalPublisher;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "remoteflow.websocket.enabled", havingValue = "true")
public class StompRealtimeSignalPublisher implements RealtimeSignalPublisher {

    private static final Logger logger = LoggerFactory.getLogger(StompRealtimeSignalPublisher.class);
    private final SimpMessagingTemplate messagingTemplate;

    public StompRealtimeSignalPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publishToRole(String role, String type, Long entityId) {
        String destination = "/topic/roles/" + role;
        logger.debug("event=WS_SIGNAL_ROLE type={} role={} entityId={}", type, role, entityId);
        messagingTemplate.convertAndSend(destination, createPayload(type, entityId));
    }

    @Override
    public void publishToUser(String username, String type, Long entityId) {
        String destination = "/queue/signals";
        logger.debug("event=WS_SIGNAL_USER type={} username={} entityId={}", type, username, entityId);
        messagingTemplate.convertAndSendToUser(username, destination, createPayload(type, entityId));
    }

    private Map<String, Object> createPayload(String type, Long entityId) {
        return Map.of(
                "type", type,
                "entityId", entityId
        );
    }
}
