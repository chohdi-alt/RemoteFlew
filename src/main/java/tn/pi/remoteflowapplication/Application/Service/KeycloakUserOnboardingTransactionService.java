package tn.pi.remoteflowapplication.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;

@Service
public class KeycloakUserOnboardingTransactionService {

    private final KeycloakUserSyncService keycloakUserSyncService;
    private final UserRepository userRepository;
    private final ActivationTokenService activationTokenService;

    public KeycloakUserOnboardingTransactionService(
            KeycloakUserSyncService keycloakUserSyncService,
            UserRepository userRepository,
            ActivationTokenService activationTokenService) {
        this.keycloakUserSyncService = keycloakUserSyncService;
        this.userRepository = userRepository;
        this.activationTokenService = activationTokenService;
    }

    @Transactional
    public OnboardingResult synchronizeAndIssueActivationToken(String keycloakUserId) {
        keycloakUserSyncService.synchronizeUsersAndRoles();

        User user = userRepository.findByKeycloakId(keycloakUserId)
                .orElseThrow(() -> new BusinessException("User synchronization failed for: " + keycloakUserId));

        ActivationTokenService.IssuedActivationToken issuedToken = activationTokenService.issueToken(
                user,
                keycloakUserId,
                user.getUsername(),
                user.getEmail());

        return new OnboardingResult(user, issuedToken);
    }

    public record OnboardingResult(
            User user,
            ActivationTokenService.IssuedActivationToken issuedToken) {
    }
}
