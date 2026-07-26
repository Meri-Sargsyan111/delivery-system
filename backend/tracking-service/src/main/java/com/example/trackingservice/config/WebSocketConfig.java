package com.example.trackingservice.config;

import com.example.trackingservice.ws.TrackingChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Live tracking broadcasts - /topic/tracking/order/{orderId} (customer/assigned courier/
 * admin) and /topic/tracking/admin/live (admin firehose), both authorization-checked via
 * TrackingChannelInterceptor (same JWT-on-CONNECT pattern as chat-service). Read-only:
 * no application destination prefix is registered since clients never send anything here.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TrackingChannelInterceptor trackingChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-tracking")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(trackingChannelInterceptor);
    }
}
