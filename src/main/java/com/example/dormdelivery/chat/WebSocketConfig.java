package com.example.dormdelivery.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final ChatAccess access;
    private final String[] allowedOrigins;
    public WebSocketConfig(ChatAccess access,@org.springframework.beans.factory.annotation.Value("${app.allowed-origins:http://localhost:5173,http://localhost:8080}") String origins) {
        this.access=access;this.allowedOrigins=origins.split(",");
    }
    @Override public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(access);
    }
    @Override public void configureClientOutboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(new org.springframework.messaging.support.ChannelInterceptor() {
            @Override public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message,org.springframework.messaging.MessageChannel channel) { return access.outbound(message); }
        });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {

        // 서버 -> 클라이언트
        config.enableSimpleBroker("/topic");

        // 클라이언트 -> 서버
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        // WebSocket 연결 주소
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(new SessionHandshake());
    }
}
