package com.example.trackingservice.config;

import com.example.trackingservice.event.DeliveryUpdateEvent;
import com.example.trackingservice.event.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    /**
     * A malformed/incompatible payload throws during deserialization; without
     * ErrorHandlingDeserializer that happens outside the listener, isn't caught by the
     * container's error handler, and wedges the partition in a poison-pill retry loop.
     * Wrapping it routes deserialization failures through the same DefaultErrorHandler
     * below, which logs and skips after a couple of retries instead of blocking forever.
     */
    private DefaultErrorHandler skipAfterRetriesErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 2));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DeliveryUpdateEvent> deliveryUpdateKafkaListenerContainerFactory() {
        JsonDeserializer<DeliveryUpdateEvent> deserializer = new JsonDeserializer<>(DeliveryUpdateEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);

        ConcurrentKafkaListenerContainerFactory<String, DeliveryUpdateEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(), new ErrorHandlingDeserializer<>(new StringDeserializer()), new ErrorHandlingDeserializer<>(deserializer)));
        factory.setCommonErrorHandler(skipAfterRetriesErrorHandler());
        return factory;
    }

    /**
     * New consumption of new-orders, added solely to learn each order's owning
     * customerUserId for the local OrderOwnership authorization projection.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> orderCreatedKafkaListenerContainerFactory() {
        JsonDeserializer<OrderCreatedEvent> deserializer = new JsonDeserializer<>(OrderCreatedEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);

        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(), new ErrorHandlingDeserializer<>(new StringDeserializer()), new ErrorHandlingDeserializer<>(deserializer)));
        factory.setCommonErrorHandler(skipAfterRetriesErrorHandler());
        return factory;
    }
}
