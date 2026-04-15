package tn.pi.remoteflowapplication.infrastructure.security;

import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;
import tn.pi.remoteflowapplication.domain.entity.User;

@Component
public class KeycloakUserMapper {

    public User toDomainUser(UserRepresentation kcUser) {

        return new User(
                kcUser.getId(),
                kcUser.getFirstName() + " " + kcUser.getLastName(),
                kcUser.getEmail()
        );
    }
}
