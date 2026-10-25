package com.realchat.config;

import com.realchat.security.JwtService;
import com.realchat.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.Set;


@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final Set<String> ALLOWED_SUBSCRIPTIONS = Set.of("/user/queue/events", "/user/queue/errors", "/topic/presence");
    private final JwtService jwt;
    private final SessionService sessions;
    @Value("${app.frontend-origin}") private String frontendOrigin;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(frontendOrigin).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (acc == null || acc.getCommand() == null) return message;
                StompCommand cmd = acc.getCommand();
                if (StompCommand.CONNECT.equals(cmd)) {
                    String h = acc.getFirstNativeHeader("Authorization");
                    Long id = (h != null && h.startsWith("Bearer ")) ? jwt.parse(h.substring(7)).filter(t -> sessions.validate(t.sid(), t.userId())).map(JwtService.TokenInfo::userId).orElse(null) : null;
                    if (id == null) throw new MessagingException("Unauthorized");
                    acc.setUser(new UsernamePasswordAuthenticationToken(String.valueOf(id), null, List.of()));
                } else if (StompCommand.SUBSCRIBE.equals(cmd)) {
                    if (acc.getUser() == null || !ALLOWED_SUBSCRIPTIONS.contains(acc.getDestination()))
                        throw new MessagingException("Subscription denied");
                } else if (StompCommand.SEND.equals(cmd)) {
                    String d = acc.getDestination();
                    if (acc.getUser() == null || d == null || !d.startsWith("/app/")) throw new MessagingException("Send denied");
                }
                return message;
            }
        });
    }
}
