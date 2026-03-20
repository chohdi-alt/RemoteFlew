package tn.pi.remoteflowapplication.application.service;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KeycloakLoginSyncService {

    private final CurrentUserResolverService currentUserResolverService;

    public KeycloakLoginSyncService(CurrentUserResolverService currentUserResolverService) {
        this.currentUserResolverService = currentUserResolverService;
    }

    @EventListener
    @Transactional
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
            currentUserResolverService.resolveCurrentUser(jwtAuth.getToken());
        }
    }
}
