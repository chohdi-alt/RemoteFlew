package tn.pi.remoteflowapplication.config.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(name = "remoteflow.websocket.enabled", havingValue = "true")
public class WebSocketStompConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;
    private final WebSocketHandshakeInterceptor webSocketHandshakeInterceptor;

    public WebSocketStompConfig(WebSocketProperties properties, WebSocketHandshakeInterceptor webSocketHandshakeInterceptor) {
        this.properties = properties;
        this.webSocketHandshakeInterceptor = webSocketHandshakeInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(webSocketHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableStompBrokerRelay("/topic", "/queue", "/user")
                .setRelayHost(properties.getRelayHost())
                .setRelayPort(properties.getRelayPort())
                .setClientLogin(properties.getRelayClientLogin())
                .setClientPasscode(properties.getRelayClientPasscode())
                .setSystemLogin(properties.getRelaySystemLogin())
                .setSystemPasscode(properties.getRelaySystemPasscode());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
}
