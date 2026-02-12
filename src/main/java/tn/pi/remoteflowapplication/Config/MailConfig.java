package tn.pi.remoteflowapplication.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public JavaMailSender javaMailSender(MailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        if (properties.getHost() != null) {
            sender.setHost(properties.getHost());
        }
        Integer port = properties.getPort();
        if (port != null) {
            sender.setPort(port);
        }
        if (properties.getUsername() != null) {
            sender.setUsername(properties.getUsername());
        }
        if (properties.getPassword() != null) {
            sender.setPassword(properties.getPassword());
        }
        if (properties.getProtocol() != null) {
            sender.setProtocol(properties.getProtocol());
        }
        Charset encoding = properties.getDefaultEncoding();
        if (encoding != null) {
            sender.setDefaultEncoding(encoding.name());
        }

        Map<String, String> extra = properties.getProperties();
        if (extra != null && !extra.isEmpty()) {
            Properties javaMailProps = sender.getJavaMailProperties();
            javaMailProps.putAll(extra);
        }

        return sender;
    }
}
