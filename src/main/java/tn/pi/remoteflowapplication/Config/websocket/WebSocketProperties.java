package tn.pi.remoteflowapplication.config.websocket;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "remoteflow.websocket")
public class WebSocketProperties {
    private boolean enabled = false;
    private String relayHost = "localhost";
    private int relayPort = 61613;
    private String relayClientLogin = "admin";
    private String relayClientPasscode = "admin";
    private String relaySystemLogin = "admin";
    private String relaySystemPasscode = "admin";
}