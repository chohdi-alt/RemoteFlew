package tn.pi.remoteflowapplication.application.command;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tn.pi.remoteflowapplication.application.dto.CreateTeleworkDTO;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.service.DomainEventPublisher;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.rule.QuotaValidationRule;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
public class CreateTeleworkRequestHandler {

    private final TeleworkRequestRepository repository;
    private final UserRepository userRepository;
    private final DocumentStoragePort documentService;
    private final QuotaValidationRule quotaRule;
    private final DomainEventPublisher domainEventPublisher;

    private final String teleworkFolderId;

    public CreateTeleworkRequestHandler(
            TeleworkRequestRepository repository,
            UserRepository userRepository,
            DocumentStoragePort documentService,
            QuotaValidationRule quotaRule,
            DomainEventPublisher domainEventPublisher,
            @org.springframework.beans.factory.annotation.Value("${alfresco.telework-folder-id}") String teleworkFolderId) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.documentService = documentService;
        this.quotaRule = quotaRule;
        this.domainEventPublisher = domainEventPublisher;
        this.teleworkFolderId = teleworkFolderId;
    }

    @Transactional
    public Long handle(CreateTeleworkDTO dto, MultipartFile justificatif)
            throws IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("Utilisateur non authentifie");
        }

        String identity = auth.getName();
        User currentUser = userRepository.findByUsername(identity)
                .or(() -> userRepository.findByKeycloakId(identity))
                .orElseThrow(() -> new BusinessException(
                        "Profil utilisateur introuvable pour : " + identity + ". Veuillez vous reconnecter."));

        return handle(dto, justificatif, currentUser);
    }

    @Transactional
    public Long handle(CreateTeleworkDTO dto, MultipartFile justificatif, User currentUser)
            throws IOException {
        if (currentUser == null) {
            throw new BusinessException("Utilisateur authentifie invalide");
        }

        String employeeUsername = currentUser.getUsername();
        if (employeeUsername == null || employeeUsername.isBlank()) {
            throw new BusinessException("Identifiant utilisateur manquant");
        }

        TeleworkRequest request = TeleworkRequest.create(
                employeeUsername,
                dto.getStartDate(),
                dto.getEndDate());

        request.setCreateur(currentUser);
        if (currentUser.getEquipe() != null) {
            request.setEquipe(currentUser.getEquipe());
            request.assignTeamId(currentUser.getEquipe().getId());
        }

        boolean hasJustificatif = justificatif != null && !justificatif.isEmpty();

        quotaRule.validate(request, hasJustificatif);

        boolean specialCase = quotaRule.isSpecialCase(request);

        if (specialCase) {
            request.markAsSpecial();
        }

        if (hasJustificatif) {
            String nodeId = documentService.upload(justificatif, teleworkFolderId);
            request.attachJustificatif(nodeId);
        }

        repository.save(request);
        domainEventPublisher.publishEvents(request);

        return request.getId();
    }
}
