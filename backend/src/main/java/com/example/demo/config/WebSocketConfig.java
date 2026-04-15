package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.ws.broker.relay-enabled:false}")
    private boolean relayEnabled;

    @Value("${app.ws.broker.relay-host:localhost}")
    private String relayHost;

    @Value("${app.ws.broker.relay-port:61613}")
    private int relayPort;

    @Value("${app.ws.broker.client-login:guest}")
    private String clientLogin;

    @Value("${app.ws.broker.client-passcode:guest}")
    private String clientPasscode;

    @Value("${app.ws.broker.system-login:guest}")
    private String systemLogin;

    @Value("${app.ws.broker.system-passcode:guest}")
    private String systemPasscode;

    @Value("${app.ws.broker.virtual-host:/}")
    private String virtualHost;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        if (relayEnabled) {
            config.enableStompBrokerRelay("/topic")
                    .setRelayHost(relayHost)
                    .setRelayPort(relayPort)
                    .setClientLogin(clientLogin)
                    .setClientPasscode(clientPasscode)
                    .setSystemLogin(systemLogin)
                    .setSystemPasscode(systemPasscode)
                    .setVirtualHost(virtualHost);
        } else {
            config.enableSimpleBroker("/topic");
        }

        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*");

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
