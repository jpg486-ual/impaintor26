package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.test.util.ReflectionTestUtils;

class WebSocketConfigTests {

    @Test
    void usesSimpleBrokerWhenRelayDisabled() {
        WebSocketConfig config = new WebSocketConfig();
        ReflectionTestUtils.setField(config, "relayEnabled", false);

        MessageBrokerRegistry registry = new MessageBrokerRegistry(
                new ExecutorSubscribableChannel(),
                new ExecutorSubscribableChannel());
        config.configureMessageBroker(registry);

        ExecutorSubscribableChannel brokerChannel = new ExecutorSubscribableChannel();
        SimpleBrokerMessageHandler simpleBroker = ReflectionTestUtils.invokeMethod(
                registry,
                "getSimpleBroker",
                brokerChannel);
        StompBrokerRelayMessageHandler relayBroker = ReflectionTestUtils.invokeMethod(
                registry,
                "getStompBrokerRelay",
                brokerChannel);

        assertThat(simpleBroker).isNotNull();
        assertThat(relayBroker).isNull();
        assertThat(simpleBroker.getDestinationPrefixes()).containsExactly("/topic");
    }

    @Test
    void usesStompRelayWhenRelayEnabled() {
        WebSocketConfig config = new WebSocketConfig();
        ReflectionTestUtils.setField(config, "relayEnabled", true);
        ReflectionTestUtils.setField(config, "relayHost", "rabbitmq");
        ReflectionTestUtils.setField(config, "relayPort", 61613);
        ReflectionTestUtils.setField(config, "clientLogin", "guest");
        ReflectionTestUtils.setField(config, "clientPasscode", "guest");
        ReflectionTestUtils.setField(config, "systemLogin", "guest");
        ReflectionTestUtils.setField(config, "systemPasscode", "guest");
        ReflectionTestUtils.setField(config, "virtualHost", "/");

        MessageBrokerRegistry registry = new MessageBrokerRegistry(
                new ExecutorSubscribableChannel(),
                new ExecutorSubscribableChannel());
        config.configureMessageBroker(registry);

        ExecutorSubscribableChannel brokerChannel = new ExecutorSubscribableChannel();
        StompBrokerRelayMessageHandler relayBroker = ReflectionTestUtils.invokeMethod(
                registry,
                "getStompBrokerRelay",
                brokerChannel);
        SimpleBrokerMessageHandler simpleBroker = ReflectionTestUtils.invokeMethod(
                registry,
                "getSimpleBroker",
                brokerChannel);

        assertThat(relayBroker).isNotNull();
        assertThat(simpleBroker).isNull();
        assertThat(relayBroker.getDestinationPrefixes()).containsExactly("/topic");
        assertThat(relayBroker.getRelayHost()).isEqualTo("rabbitmq");
        assertThat(relayBroker.getRelayPort()).isEqualTo(61613);
        assertThat(relayBroker.getClientLogin()).isEqualTo("guest");
        assertThat(relayBroker.getClientPasscode()).isEqualTo("guest");
        assertThat(relayBroker.getSystemLogin()).isEqualTo("guest");
        assertThat(relayBroker.getSystemPasscode()).isEqualTo("guest");
        assertThat(relayBroker.getVirtualHost()).isEqualTo("/");
    }
}
