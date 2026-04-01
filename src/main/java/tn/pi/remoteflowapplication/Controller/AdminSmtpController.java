package tn.pi.remoteflowapplication.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.pi.remoteflowapplication.application.dto.SmtpConfigRequest;
import tn.pi.remoteflowapplication.application.dto.SmtpConfigResponse;
import tn.pi.remoteflowapplication.application.dto.SmtpConnectionTestResponse;
import tn.pi.remoteflowapplication.application.service.SmtpConfigurationService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/smtp")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSmtpController {

    private final SmtpConfigurationService smtpConfigurationService;

    public AdminSmtpController(SmtpConfigurationService smtpConfigurationService) {
        this.smtpConfigurationService = smtpConfigurationService;
    }

    @GetMapping
    public List<SmtpConfigResponse> list() {
        return smtpConfigurationService.findAll();
    }

    @GetMapping("/effective")
    public SmtpConfigResponse getEffectiveConfiguration() {
        return smtpConfigurationService.getEffectiveConfiguration();
    }

    @PostMapping
    public ResponseEntity<SmtpConfigResponse> create(@RequestBody @Valid SmtpConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(smtpConfigurationService.create(request));
    }

    @PutMapping("/{id}")
    public SmtpConfigResponse update(
            @PathVariable Long id,
            @RequestBody @Valid SmtpConfigRequest request) {
        return smtpConfigurationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        smtpConfigurationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public SmtpConfigResponse activate(@PathVariable Long id) {
        return smtpConfigurationService.activate(id);
    }

    @PostMapping("/{id}/test")
    public SmtpConnectionTestResponse testStoredConfiguration(@PathVariable Long id) {
        return smtpConfigurationService.testConnectionById(id);
    }

    @PostMapping("/test")
    public SmtpConnectionTestResponse testInputConfiguration(@RequestBody @Valid SmtpConfigRequest request) {
        return smtpConfigurationService.testConnection(request);
    }
}
