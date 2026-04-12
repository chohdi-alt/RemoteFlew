package tn.pi.remoteflowapplication.config.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompJwtChannelInterceptorAuthorizationTest {

    private final StompJwtChannelInterceptor interceptor =
            new StompJwtChannelInterceptor(mock(JwtDecoder.class), new JwtAuthenticationConverter());

    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void connectRequiresAuthenticatedPrincipal() {
        Message<byte[]> connect = connectMessage(null);

        assertThatThrownBy(() -> interceptor.preSend(connect, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required for websocket connect");
    }

    @Test
    void connectAcceptsValidBearerToken() {
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        JwtAuthenticationConverter converter = mock(JwtAuthenticationConverter.class);
        StompJwtChannelInterceptor connectInterceptor = new StompJwtChannelInterceptor(jwtDecoder, converter);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "manager-1")
                .build();

        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(converter.convert(jwt)).thenReturn(authentication("manager-1", "MANAGER", "websocket:subscribe"));

        Message<byte[]> connect = connectMessage("Bearer token");

        assertThatCode(() -> connectInterceptor.preSend(connect, channel))
                .doesNotThrowAnyException();
    }

    @Test
    void userCanSubscribeOwnTopic() {
        Message<byte[]> subscribe = subscribeMessage(
                "/topic/user/user-1",
                authentication("user-1", "USER", "websocket:subscribe"));

        assertThatCode(() -> interceptor.preSend(subscribe, channel))
                .doesNotThrowAnyException();
    }

    @Test
    void employeeRoleAliasCanSubscribeOwnUserTopic() {
        Message<byte[]> subscribe = subscribeMessage(
                "/topic/user/user-1",
                authentication("user-1", "EMPLOYEE", "websocket:subscribe"));

        assertThatCode(() -> interceptor.preSend(subscribe, channel))
                .doesNotThrowAnyException();
    }

    @Test
    void userCannotSubscribeOtherUserTopic() {
        Message<byte[]> subscribe = subscribeMessage(
                "/topic/user/user-2",
                authentication("user-1", "USER", "websocket:subscribe"));

        assertThatThrownBy(() -> interceptor.preSend(subscribe, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unauthorized user topic");
    }

    @Test
    void managerCanSubscribeOwnManagerTopic() {
        Message<byte[]> subscribe = subscribeMessage(
                "/topic/manager/manager-1",
                authentication("manager-1", "MANAGER", "websocket:subscribe"));

        assertThatCode(() -> interceptor.preSend(subscribe, channel))
                .doesNotThrowAnyException();
    }

    @Test
    void managerCannotSubscribeOtherManagerTopic() {
        Message<byte[]> subscribe = subscribeMessage(
                "/topic/manager/manager-2",
                authentication("manager-1", "MANAGER", "websocket:subscribe"));

        assertThatThrownBy(() -> interceptor.preSend(subscribe, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unauthorized manager topic");
    }

    @Test
    void managerCanSubscribeOwnTeamTopic() {
        Message<byte[]> subscribe = subscribeMessage(
                "/topic/team/manager-1",
                authentication("manager-1", "MANAGER", "websocket:subscribe"));

        assertThatCode(() -> interceptor.preSend(subscribe, channel))
                .doesNotThrowAnyException();
    }

    @Test
    void hrCanSubscribeHrAndRequestsTopics() {
        Message<byte[]> hrSubscribe = subscribeMessage(
                "/topic/hr/inbox",
                authentication("hr-1", "HR", "websocket:subscribe"));
        Message<byte[]> requestSubscribe = subscribeMessage(
                "/topic/requests/pending",
                authentication("hr-1", "HR", "websocket:subscribe"));

        assertThatCode(() -> interceptor.preSend(hrSubscribe, channel))
                .doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(requestSubscribe, channel))
                .doesNotThrowAnyException();
    }

    @Test
    void nonHrCannotSubscribeHrTopics() {
        Message<byte[]> subscribe = subscribeMessage(
                "/topic/hr/inbox",
                authentication("user-1", "USER", "websocket:subscribe"));

        assertThatThrownBy(() -> interceptor.preSend(subscribe, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("HR only");
    }

    @Test
    void adminCanSubscribeAnyTopic() {
        Message<byte[]> subscribe = subscribeMessage(
                "/topic/anything/custom",
                authentication("admin-1", "ADMIN", "websocket:subscribe"));

        assertThatCode(() -> interceptor.preSend(subscribe, channel))
                .doesNotThrowAnyException();
    }

    @Test
    void roleWithoutScopeIsDenied() {
        Message<byte[]> subscribe = subscribeMessage(
                "/topic/manager/manager-1",
                authenticationWithoutScopes("manager-1", "MANAGER"));

        assertThatThrownBy(() -> interceptor.preSend(subscribe, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient scope");
    }

    @Test
    void wildcardSubscriptionsAreBlocked() {
        Message<byte[]> subscribe = subscribeMessage(
                "/topic/user/*",
                authentication("user-1", "USER", "websocket:subscribe"));

        assertThatThrownBy(() -> interceptor.preSend(subscribe, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Wildcard subscriptions are not allowed");
    }

    @Test
    void unknownDestinationIsDeniedByDefault() {
        Message<byte[]> subscribe = subscribeMessage(
                "/topic/unknown/channel",
                authentication("user-1", "USER", "websocket:subscribe"));

        assertThatThrownBy(() -> interceptor.preSend(subscribe, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unauthorized subscription");
    }

    @Test
    void legacyDestinationsRemainAuthorized() {
        Message<byte[]> managerLegacy = subscribeMessage(
                "/topic/roles/MANAGER",
                authentication("manager-1", "MANAGER", "websocket:subscribe"));
        Message<byte[]> privateQueue = subscribeMessage(
                "/user/queue/signals",
                authentication("user-1", "USER", "websocket:subscribe"));

        assertThatCode(() -> interceptor.preSend(managerLegacy, channel))
                .doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(privateQueue, channel))
                .doesNotThrowAnyException();
    }

    private Message<byte[]> subscribeMessage(String destination, Authentication authentication) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination(destination);
        accessor.setUser(authentication);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> connectMessage(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authorizationHeader != null) {
            accessor.addNativeHeader("Authorization", authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private UsernamePasswordAuthenticationToken authentication(String username, String role, String... scopes) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String scope : scopes) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
        return new UsernamePasswordAuthenticationToken(username, "n/a", authorities);
    }

    private UsernamePasswordAuthenticationToken authenticationWithoutScopes(String username, String role) {
        return authentication(username, role);
    }
}
