package tn.pi.remoteflowapplication.infrastructure.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.port.out.RealtimeSignalPublisher;

@Service
@ConditionalOnProperty(name = "remoteflow.websocket.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpRealtimeSignalPublisher implements RealtimeSignalPublisher {

    private static final Logger logger = LoggerFactory.getLogger(NoOpRealtimeSignalPublisher.class);

    @Override
    public void publishToRole(String role, String type, Long entityId) {
        logger.debug("event=WS_SIGNAL_DISABLED role={} type={} entityId={}", role, type, entityId);
    }

    @Override
    public void publishToUser(String username, String type, Long entityId) {
        logger.debug("event=WS_SIGNAL_DISABLED username={} type={} entityId={}", username, type, entityId);
    }
}
