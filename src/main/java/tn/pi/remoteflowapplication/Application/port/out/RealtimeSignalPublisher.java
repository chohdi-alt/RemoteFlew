package tn.pi.remoteflowapplication.application.port.out;

public interface RealtimeSignalPublisher {
    void publishToRole(String role, String type, Long entityId);
    void publishToUser(String username, String type, Long entityId);
}
