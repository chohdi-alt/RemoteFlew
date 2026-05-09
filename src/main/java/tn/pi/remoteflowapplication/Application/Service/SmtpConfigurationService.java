package tn.pi.remoteflowapplication.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import tn.pi.remoteflowapplication.application.dto.SmtpConfigRequest;
import tn.pi.remoteflowapplication.application.dto.SmtpConfigResponse;
import tn.pi.remoteflowapplication.application.dto.SmtpConnectionTestResponse;
import tn.pi.remoteflowapplication.domain.entity.SmtpConfig;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringSmtpConfigJpaRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

@Service
public class SmtpConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(SmtpConfigurationService.class);
    private static final String DB_SOURCE = "DATABASE";
    private static final String APP_PROPS_SOURCE = "APPLICATION_PROPERTIES";

    private final SpringSmtpConfigJpaRepository smtpConfigRepository;
    private final MailProperties mailProperties;
    private final String notificationFromEmail;

    public SmtpConfigurationService(
            SpringSmtpConfigJpaRepository smtpConfigRepository,
            MailProperties mailProperties,
            @Value("${notification.from-email:}") String notificationFromEmail) {
        this.smtpConfigRepository = smtpConfigRepository;
        this.mailProperties = mailProperties;
        this.notificationFromEmail = notificationFromEmail;
    }

    @Transactional(readOnly = true)
    public List<SmtpConfigResponse> findAll() {
        log.error("[SMTP SERVICE] FETCHING CONFIGS");
        List<SmtpConfig> configs = smtpConfigRepository.findAll();
        log.error("[SMTP DB RESULT] size={}", configs.size());

        List<SmtpConfigResponse> responses = configs.stream()
                .map(config -> toResponse(config, DB_SOURCE))
                .toList();

        log.error("[SMTP RESPONSE] {}", responses);
        return responses;
    }

    @Transactional(readOnly = true)
    public SmtpConfigResponse getEffectiveConfiguration() {
        return smtpConfigRepository.findFirstByActiveTrueOrderByUpdatedAtDesc()
                .map(config -> toResponse(config, DB_SOURCE))
                .orElseGet(() -> toFallbackResponse(resolveFallbackSettings()));
    }

    @Transactional
    public SmtpConfigResponse create(SmtpConfigRequest request) {
        SmtpConfig config = new SmtpConfig(
                request.name(),
                request.host(),
                request.port(),
                normalizeProtocol(request.protocol()),
                request.username(),
                request.password(),
                request.fromEmail(),
                request.authEnabled(),
                request.starttlsEnabled(),
                request.sslEnabled(),
                request.connectionTimeoutMs(),
                request.readTimeoutMs(),
                request.writeTimeoutMs(),
                request.active());

        SmtpConfig saved = smtpConfigRepository.save(config);
        if (saved.isActive()) {
            enforceSingleActive(saved.getId());
            saved = smtpConfigRepository.findById(saved.getId()).orElse(saved);
        }
        return toResponse(saved, DB_SOURCE);
    }

    @Transactional
    public SmtpConfigResponse update(Long id, SmtpConfigRequest request) {
        SmtpConfig existing = smtpConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException("SMTP configuration not found: " + id));

        existing.update(
                request.name(),
                request.host(),
                request.port(),
                normalizeProtocol(request.protocol()),
                request.username(),
                request.password(),
                request.fromEmail(),
                request.authEnabled(),
                request.starttlsEnabled(),
                request.sslEnabled(),
                request.connectionTimeoutMs(),
                request.readTimeoutMs(),
                request.writeTimeoutMs(),
                request.active());

        SmtpConfig saved = smtpConfigRepository.save(existing);
        if (saved.isActive()) {
            enforceSingleActive(saved.getId());
            saved = smtpConfigRepository.findById(saved.getId()).orElse(saved);
        }
        return toResponse(saved, DB_SOURCE);
    }

    @Transactional
    public void delete(Long id) {
        SmtpConfig config = smtpConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException("SMTP configuration not found: " + id));
        smtpConfigRepository.delete(config);
    }

    @Transactional
    public SmtpConfigResponse activate(Long id) {
        SmtpConfig config = smtpConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException("SMTP configuration not found: " + id));
        config.activate();
        smtpConfigRepository.save(config);
        enforceSingleActive(id);
        return smtpConfigRepository.findById(id)
                .map(saved -> toResponse(saved, DB_SOURCE))
                .orElseThrow(() -> new BusinessException("SMTP configuration not found after activation: " + id));
    }

    @Transactional(readOnly = true)
    public SmtpConnectionTestResponse testConnection(SmtpConfigRequest request) {
        SmtpConnectionSettings settings = new SmtpConnectionSettings(
                request.host(),
                request.port(),
                normalizeProtocol(request.protocol()),
                request.username(),
                request.password(),
                request.fromEmail(),
                request.authEnabled(),
                request.starttlsEnabled(),
                request.sslEnabled(),
                request.connectionTimeoutMs(),
                request.readTimeoutMs(),
                request.writeTimeoutMs(),
                DB_SOURCE);
        return test(settings);
    }

    @Transactional(readOnly = true)
    public SmtpConnectionTestResponse testConnectionById(Long id) {
        SmtpConfig config = smtpConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException("SMTP configuration not found: " + id));
        return test(toConnectionSettings(config, DB_SOURCE));
    }

    @Transactional(readOnly = true)
    public SmtpConnectionSettings getEffectiveSettings() {
        return smtpConfigRepository.findFirstByActiveTrueOrderByUpdatedAtDesc()
                .map(config -> toConnectionSettings(config, DB_SOURCE))
                .orElseGet(this::resolveFallbackSettings);
    }

    @Transactional(readOnly = true)
    public Optional<SmtpConfig> findActiveConfig() {
        return smtpConfigRepository.findFirstByActiveTrueOrderByUpdatedAtDesc();
    }

    private void enforceSingleActive(Long activeId) {
        List<SmtpConfig> all = smtpConfigRepository.findAll();
        boolean changed = false;
        for (SmtpConfig config : all) {
            boolean shouldBeActive = config.getId() != null && config.getId().equals(activeId);
            if (shouldBeActive && !config.isActive()) {
                config.activate();
                changed = true;
            } else if (!shouldBeActive && config.isActive()) {
                config.deactivate();
                changed = true;
            }
        }
        if (changed) {
            smtpConfigRepository.saveAll(all);
        }
    }

    private SmtpConnectionTestResponse test(SmtpConnectionSettings settings) {
        try {
            JavaMailSenderImpl sender = buildSender(settings);
            sender.testConnection();
            return new SmtpConnectionTestResponse(
                    true,
                    "SMTP connection successful using " + settings.source() + " settings.");
        } catch (Exception ex) {
            return new SmtpConnectionTestResponse(
                    false,
                    "SMTP connection failed using " + settings.source() + " settings: " + ex.getMessage());
        }
    }

    private JavaMailSenderImpl buildSender(SmtpConnectionSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.host());
        if (settings.port() != null) {
            sender.setPort(settings.port());
        }
        sender.setProtocol(normalizeProtocol(settings.protocol()));
        if (settings.username() != null && !settings.username().isBlank()) {
            sender.setUsername(settings.username());
        }
        if (settings.password() != null && !settings.password().isBlank()) {
            sender.setPassword(settings.password());
        }

        Properties javaMailProps = sender.getJavaMailProperties();
        javaMailProps.put("mail.smtp.auth", String.valueOf(settings.authEnabled()));
        javaMailProps.put("mail.smtp.starttls.enable", String.valueOf(settings.starttlsEnabled()));
        javaMailProps.put("mail.smtp.ssl.enable", String.valueOf(settings.sslEnabled()));
        if (settings.connectionTimeoutMs() != null) {
            javaMailProps.put("mail.smtp.connectiontimeout", String.valueOf(settings.connectionTimeoutMs()));
        }
        if (settings.readTimeoutMs() != null) {
            javaMailProps.put("mail.smtp.timeout", String.valueOf(settings.readTimeoutMs()));
        }
        if (settings.writeTimeoutMs() != null) {
            javaMailProps.put("mail.smtp.writetimeout", String.valueOf(settings.writeTimeoutMs()));
        }

        return sender;
    }

    private SmtpConnectionSettings toConnectionSettings(SmtpConfig config, String source) {
        return new SmtpConnectionSettings(
                config.getHost(),
                config.getPort(),
                normalizeProtocol(config.getProtocol()),
                config.getUsername(),
                config.getPassword(),
                config.getFromEmail(),
                config.isAuthEnabled(),
                config.isStarttlsEnabled(),
                config.isSslEnabled(),
                config.getConnectionTimeoutMs(),
                config.getReadTimeoutMs(),
                config.getWriteTimeoutMs(),
                source);
    }

    private SmtpConnectionSettings resolveFallbackSettings() {
        Map<String, String> extra = mailProperties.getProperties();
        boolean authEnabled = Boolean.parseBoolean(extra.getOrDefault("mail.smtp.auth", "false"));
        boolean starttlsEnabled = Boolean.parseBoolean(extra.getOrDefault("mail.smtp.starttls.enable", "false"));
        boolean sslEnabled = Boolean.parseBoolean(extra.getOrDefault("mail.smtp.ssl.enable", "false"));
        Integer connectionTimeoutMs = parseInteger(extra.get("mail.smtp.connectiontimeout"));
        Integer readTimeoutMs = parseInteger(extra.get("mail.smtp.timeout"));
        Integer writeTimeoutMs = parseInteger(extra.get("mail.smtp.writetimeout"));

        return new SmtpConnectionSettings(
                mailProperties.getHost(),
                mailProperties.getPort(),
                normalizeProtocol(mailProperties.getProtocol()),
                mailProperties.getUsername(),
                mailProperties.getPassword(),
                resolveFallbackFromEmail(),
                authEnabled,
                starttlsEnabled,
                sslEnabled,
                connectionTimeoutMs,
                readTimeoutMs,
                writeTimeoutMs,
                APP_PROPS_SOURCE);
    }

    private SmtpConfigResponse toResponse(SmtpConfig config, String source) {
        return new SmtpConfigResponse(
                config.getId(),
                config.getName(),
                config.getHost(),
                config.getPort(),
                normalizeProtocol(config.getProtocol()),
                config.getUsername(),
                config.getPassword() != null && !config.getPassword().isBlank(),
                config.getFromEmail(),
                config.isAuthEnabled(),
                config.isStarttlsEnabled(),
                config.isSslEnabled(),
                config.getConnectionTimeoutMs(),
                config.getReadTimeoutMs(),
                config.getWriteTimeoutMs(),
                config.isActive(),
                source,
                config.getUpdatedAt());
    }

    private SmtpConfigResponse toFallbackResponse(SmtpConnectionSettings settings) {
        return new SmtpConfigResponse(
                null,
                "application.properties",
                settings.host(),
                settings.port(),
                normalizeProtocol(settings.protocol()),
                settings.username(),
                settings.password() != null && !settings.password().isBlank(),
                settings.fromEmail(),
                settings.authEnabled(),
                settings.starttlsEnabled(),
                settings.sslEnabled(),
                settings.connectionTimeoutMs(),
                settings.readTimeoutMs(),
                settings.writeTimeoutMs(),
                false,
                settings.source(),
                null);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveFallbackFromEmail() {
        if (notificationFromEmail != null && !notificationFromEmail.isBlank()) {
            return notificationFromEmail.trim();
        }
        if (mailProperties.getUsername() != null && !mailProperties.getUsername().isBlank()) {
            return mailProperties.getUsername().trim();
        }
        return null;
    }

    private String normalizeProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return "smtp";
        }
        return protocol.trim().toLowerCase();
    }

    public record SmtpConnectionSettings(
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
            String source) {
    }
}