package com.macro.mall.portal.config;

import com.macro.mall.portal.util.JwtTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = accessor.getFirstNativeHeader("token");
                    if (token != null && jwtTokenUtil.validateToken(token)) {
                        Long memberId = jwtTokenUtil.getMemberIdFromToken(token);
                        String username = jwtTokenUtil.getUsernameFromToken(token);
                        Principal user = new StompPrincipal(memberId, username != null ? username : memberId.toString());
                        accessor.setUser(user);
                        log.debug("WebSocket认证成功: memberId={}", memberId);
                    } else {
                        log.warn("WebSocket认证失败: token无效");
                    }
                }
                return message;
            }
        });
    }

    private static class StompPrincipal implements Principal {
        private final Long id;
        private final String name;

        StompPrincipal(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        public Long getId() {
            return id;
        }
    }
}
