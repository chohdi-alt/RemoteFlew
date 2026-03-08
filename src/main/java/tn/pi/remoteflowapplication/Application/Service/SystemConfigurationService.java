package tn.pi.remoteflowapplication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SystemConfigurationService implements ApplicationRunner {

    public static final String TELEWORK_MAX_DAYS_KEY = "telework.max-days-per-week";

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigurationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AtomicInteger cachedMaxDaysPerWeek = new AtomicInteger(1);

    @Value("${telework.max-days-per-week:1}")
    private int defaultMaxDaysPerWeek;

    public SystemConfigurationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        int fallback = sanitize(defaultMaxDaysPerWeek);
        cachedMaxDaysPerWeek.set(fallback);
        Integer dbValue = findInteger(TELEWORK_MAX_DAYS_KEY);
        if (dbValue != null) {
            cachedMaxDaysPerWeek.set(sanitize(dbValue));
        }

        logger.info("event=TELEWORK_QUOTA_CONFIG_LOADED key={} value={}",
                TELEWORK_MAX_DAYS_KEY, cachedMaxDaysPerWeek.get());
    }

    public int getTeleworkMaxDaysPerWeek() {
        return sanitize(cachedMaxDaysPerWeek.get());
    }

    public int updateTeleworkMaxDaysPerWeek(int maxDaysPerWeek) {
        int sanitized = sanitize(maxDaysPerWeek);
        jdbcTemplate.update(
                """
                        INSERT INTO system_configuration (config_key, config_value, updated_at)
                        VALUES (?, ?, CURRENT_TIMESTAMP)
                        ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_at = CURRENT_TIMESTAMP
                        """,
                TELEWORK_MAX_DAYS_KEY,
                String.valueOf(sanitized));
        cachedMaxDaysPerWeek.set(sanitized);

        logger.info("event=TELEWORK_QUOTA_CONFIG_UPDATED key={} value={}",
                TELEWORK_MAX_DAYS_KEY, sanitized);
        return sanitized;
    }

    private Integer findInteger(String key) {
        try {
            String value = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM system_configuration WHERE config_key = ?",
                    String.class,
                    key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return Integer.parseInt(value.trim());
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        } catch (Exception ex) {
            logger.warn("event=SYSTEM_CONFIGURATION_READ_FAILED key={} message={}", key, ex.getMessage());
            return null;
        }
    }

    private int sanitize(int value) {
        return value > 0 ? value : 1;
    }
}
