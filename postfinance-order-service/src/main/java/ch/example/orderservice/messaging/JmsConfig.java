package ch.example.orderservice.messaging;

import org.apache.activemq.artemis.jms.client.ActiveMQQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Queue;

/**
 * Central JMS wiring:
 * <ul>
 *   <li>Queue names as constants (single source of truth).</li>
 *   <li>JSON message conversion so the payload is human-readable and schema-friendly.</li>
 *   <li>A listener factory with redelivery back-off; after N attempts Artemis moves the
 *       message to the Dead-Letter-Queue (configured in application.yml).</li>
 * </ul>
 */
@Configuration
public class JmsConfig {

    public static final String ORDER_CREATED_QUEUE = "order.created";

    @Bean
    public Queue orderCreatedQueue() {
        return new ActiveMQQueue(ORDER_CREATED_QUEUE);
    }

    /** Serialize message bodies as JSON and keep the Java type in a header for the consumer. */
    @Bean
    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper); // Spring's mapper has JavaTimeModule (Instant) registered
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    /**
     * Listener container factory with transacted sessions: if the listener throws,
     * the message is rolled back and redelivered (with the back-off configured on the broker),
     * eventually landing in the DLQ.
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer,
            MessageConverter jacksonJmsMessageConverter) {

        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jacksonJmsMessageConverter);
        factory.setSessionTransacted(true);
        return factory;
    }
}





