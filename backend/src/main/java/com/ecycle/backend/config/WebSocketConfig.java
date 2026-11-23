package com.ecycle.backend.config;

import com.ecycle.backend.repository.TokenRevocationRepository;
import com.ecycle.backend.repository.UserRepository;
import com.ecycle.shared.Enums.AccountStatus;
import io.jsonwebtoken.Claims;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket at /ws. The JWT is sent in the CONNECT frame's Authorization header (never in the URL). Clients may only subscribe to their own /user/queue/** destinations;
 * they cannot publish application messages (the server pushes events; REST remains the write path), so there is nothing to spoof.
 */
@Configuration @EnableWebSocketMessageBroker @RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    private final JwtService jwt; private final UserRepository users; private final TokenRevocationRepository revoked;

    @Override public void registerStompEndpoints(StompEndpointRegistry r) { r.addEndpoint("/ws").setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*"); }
    @Override public void configureMessageBroker(MessageBrokerRegistry r) { r.enableSimpleBroker("/queue"); r.setUserDestinationPrefix("/user"); r.setApplicationDestinationPrefixes("/app"); }

    @Override
    public void configureClientInboundChannel(ChannelRegistration reg) {
        reg.interceptors(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> m, MessageChannel ch) {
                StompHeaderAccessor a = MessageHeaderAccessor.getAccessor(m, StompHeaderAccessor.class);
                if (a == null || a.getCommand() == null) return m;
                if (StompCommand.CONNECT.equals(a.getCommand())) {
                    String h = a.getFirstNativeHeader("Authorization");
                    if (h == null || !h.startsWith("Bearer ")) throw new MessagingException("Authentication required");
                    try {
                        Claims c = jwt.parse(h.substring(7));
                        if (revoked.existsByJti(c.getId())) throw new MessagingException("Token revoked");
                        var u = users.findById(Long.valueOf(c.getSubject())).filter(x -> x.getStatus() == AccountStatus.ACTIVE).orElseThrow(() -> new MessagingException("Account unavailable"));
                        final String name = String.valueOf(u.getId());
                        a.setUser((Principal) () -> name);
                    } catch (MessagingException e) { throw e; }
                    catch (RuntimeException e) { log.debug("WebSocket auth rejected: {}", e.getMessage()); throw new MessagingException("Invalid token"); }
                } else if (a.getUser() == null) {
                    throw new MessagingException("Not authenticated");
                } else if (StompCommand.SUBSCRIBE.equals(a.getCommand())) {
                    String d = a.getDestination();
                    if (d == null || !d.startsWith("/user/queue/")) throw new MessagingException("Subscription not allowed");
                } else if (StompCommand.SEND.equals(a.getCommand())) {
                    throw new MessagingException("Clients cannot publish; use the REST API");
                }
                return m;
            }
        });
    }
}
