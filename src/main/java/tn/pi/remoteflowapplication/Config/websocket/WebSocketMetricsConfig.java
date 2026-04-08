package tn.pi.remoteflowapplication.config.websocket;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "remoteflow.websocket.enabled", havingValue = "true")
public class WebSocketMetricsConfig {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketMetricsConfig.class);
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    public WebSocketMetricsConfig(MeterRegistry meterRegistry) {
        Gauge.builder("websocket.connections.active", activeConnections, AtomicInteger::get)
                .description("Number of active WebSocket connections")
                .register(meterRegistry);
        logger.info("WebSocket metrics tracking initialized");
    }

    @EventListener
    public void onSessionConnect(SessionConnectEvent event) {
        int count = activeConnections.incrementAndGet();
        logger.debug("WebSocket session connected. Active connections: {}", count);
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        int count = activeConnections.decrementAndGet();
        logger.debug("WebSocket session disconnected. Active connections: {}", count);
    }
}
