package tn.pi.remoteflowapplication.config.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@ConditionalOnProperty(name = "remoteflow.websocket.enabled", havingValue = "true")
public class WebSocketInboundChannelConfig implements WebSocketMessageBrokerConfigurer {

    private final StompJwtChannelInterceptor stompJwtChannelInterceptor;

    public WebSocketInboundChannelConfig(StompJwtChannelInterceptor stompJwtChannelInterceptor) {
        this.stompJwtChannelInterceptor = stompJwtChannelInterceptor;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompJwtChannelInterceptor);
    }
}
