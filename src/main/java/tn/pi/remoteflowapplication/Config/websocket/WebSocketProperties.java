package tn.pi.remoteflowapplication.config.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRelayHost() { return relayHost; }
    public void setRelayHost(String relayHost) { this.relayHost = relayHost; }
    public int getRelayPort() { return relayPort; }
    public void setRelayPort(int relayPort) { this.relayPort = relayPort; }
    public String getRelayClientLogin() { return relayClientLogin; }
    public void setRelayClientLogin(String relayClientLogin) { this.relayClientLogin = relayClientLogin; }
    public String getRelayClientPasscode() { return relayClientPasscode; }
    public void setRelayClientPasscode(String relayClientPasscode) { this.relayClientPasscode = relayClientPasscode; }
    public String getRelaySystemLogin() { return relaySystemLogin; }
    public void setRelaySystemLogin(String relaySystemLogin) { this.relaySystemLogin = relaySystemLogin; }
    public String getRelaySystemPasscode() { return relaySystemPasscode; }
    public void setRelaySystemPasscode(String relaySystemPasscode) { this.relaySystemPasscode = relaySystemPasscode; }
}