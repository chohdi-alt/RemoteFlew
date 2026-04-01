package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "smtp_configurations")
public class SmtpConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "host", nullable = false, length = 255)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "protocol", nullable = false, length = 20)
    private String protocol = "smtp";

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "password", length = 500)
    private String password;

    @Column(name = "from_email", length = 255)
    private String fromEmail;

    @Column(name = "auth_enabled", nullable = false)
    private boolean authEnabled = true;

    @Column(name = "starttls_enabled", nullable = false)
    private boolean starttlsEnabled = true;

    @Column(name = "ssl_enabled", nullable = false)
    private boolean sslEnabled = false;

    @Column(name = "connection_timeout_ms")
    private Integer connectionTimeoutMs;

    @Column(name = "read_timeout_ms")
    private Integer readTimeoutMs;

    @Column(name = "write_timeout_ms")
    private Integer writeTimeoutMs;

    @Column(name = "active", nullable = false)
    private boolean active = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SmtpConfig() {
    }

    public SmtpConfig(
            String name,
            String host,
            Integer port,
            String protocol,
            String username,
            String password,
            String fromEmail,
            boolean authEnabled,
            boolean starttlsEnabled,
            boolean sslEnabled,
            Integer connectionTimeoutMs,
            Integer readTimeoutMs,
            Integer writeTimeoutMs,
            boolean active) {
        apply(
                name,
                host,
                port,
                protocol,
                username,
                password,
                fromEmail,
                authEnabled,
                starttlsEnabled,
                sslEnabled,
                connectionTimeoutMs,
                readTimeoutMs,
                writeTimeoutMs,
                active,
                true);
    }

    public void update(
            String name,
            String host,
            Integer port,
            String protocol,
            String username,
            String password,
            String fromEmail,
            boolean authEnabled,
            boolean starttlsEnabled,
            boolean sslEnabled,
            Integer connectionTimeoutMs,
            Integer readTimeoutMs,
            Integer writeTimeoutMs,
            boolean active) {
        apply(
                name,
                host,
                port,
                protocol,
                username,
                password,
                fromEmail,
                authEnabled,
                starttlsEnabled,
                sslEnabled,
                connectionTimeoutMs,
                readTimeoutMs,
                writeTimeoutMs,
                active,
                false);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public boolean isStarttlsEnabled() {
        return starttlsEnabled;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public Integer getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public Integer getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public Integer getWriteTimeoutMs() {
        return writeTimeoutMs;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void apply(
            String name,
            String host,
            Integer port,
            String protocol,
            String username,
            String password,
            String fromEmail,
            boolean authEnabled,
            boolean starttlsEnabled,
            boolean sslEnabled,
            Integer connectionTimeoutMs,
            Integer readTimeoutMs,
            Integer writeTimeoutMs,
            boolean active,
            boolean allowPasswordReplacementWithBlank) {
        this.name = normalizeRequired(name, "default");
        this.host = normalizeRequired(host, "localhost");
        this.port = port == null ? 25 : port;
        this.protocol = normalizeRequired(protocol, "smtp");
        this.username = normalizeOptional(username);
        if (allowPasswordReplacementWithBlank) {
            this.password = normalizeOptional(password);
        } else if (password != null) {
            this.password = normalizeOptional(password);
        }
        this.fromEmail = normalizeOptional(fromEmail);
        this.authEnabled = authEnabled;
        this.starttlsEnabled = starttlsEnabled;
        this.sslEnabled = sslEnabled;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
        this.active = active;
    }

    private String normalizeRequired(String value, String fallback) {
        String normalized = normalizeOptional(value);
        return normalized == null ? fallback : normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
