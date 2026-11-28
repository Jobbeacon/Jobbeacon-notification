package com.jobbeacon.notification.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * Producers publish to the durable topic exchange; this service owns its queue and binds it to the routing keys it
 * handles. A message that keeps failing is dead-lettered to {@code notification.dlq} for a human to look at.
 */
@Configuration
public class RabbitConfiguration {

    @Bean
    public Declarables eventTopology(EventsProperties properties) {
        TopicExchange events = new TopicExchange(properties.exchange(), true, false);
        DirectExchange deadLetters = new DirectExchange(properties.deadLetterExchange(), true, false);

        Queue queue = QueueBuilder.durable(properties.queue())
            .deadLetterExchange(properties.deadLetterExchange())
            .deadLetterRoutingKey(properties.deadLetterQueue())
            .build();
        Queue deadLetterQueue = QueueBuilder.durable(properties.deadLetterQueue()).build();

        List<Declarable> declarables = new ArrayList<>(List.of(events, deadLetters, queue, deadLetterQueue,
            BindingBuilder.bind(deadLetterQueue).to(deadLetters).with(properties.deadLetterQueue())));
        for (String routingKey : properties.routingKeys()) {
            Binding binding = BindingBuilder.bind(queue).to(events).with(routingKey);
            declarables.add(binding);
        }
        return new Declarables(declarables);
    }

    /**
     * Listener retry that does not retry what cannot succeed. Spring Boot builds the listener RetryTemplate with a
     * {@link SimpleRetryPolicy} that retries every exception; this swaps in one with the same max-attempts that treats
     * {@link AmqpRejectAndDontRequeueException} (bad payload, see CONVENTIONS) as not retryable, so the recoverer
     * rejects it to the DLQ on the first failure. Causes are traversed because the container wraps the listener's
     * exception in a {@code ListenerExecutionFailedException}.
     */
    @Bean
    public RabbitRetryTemplateCustomizer listenerRetryClassifier(RabbitProperties rabbitProperties) {
        int maxAttempts = rabbitProperties.getListener().getSimple().getRetry().getMaxAttempts();
        return (target, retryTemplate) -> {
            if (target == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                retryTemplate.setRetryPolicy(new SimpleRetryPolicy(maxAttempts,
                    Map.of(AmqpRejectAndDontRequeueException.class, false), true, true));
            }
        };
    }
}
