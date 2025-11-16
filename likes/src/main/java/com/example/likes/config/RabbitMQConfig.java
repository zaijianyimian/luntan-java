package com.example.likes.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "luntanexchange";
    public static final String QUEUE_COMMENT = "commentchannel";
    public static final String QUEUE_ACTIVITY = "activitychannel";
    public static final String ROUTING_COMMENT = "comment";
    public static final String ROUTING_ACTIVITY = "activity";

    @Bean
    public Queue commentQueue() { return QueueBuilder.durable(QUEUE_COMMENT).build(); }

    @Bean
    public Queue activityQueue() { return QueueBuilder.durable(QUEUE_ACTIVITY).build(); }

    @Bean
    public DirectExchange luntanExchange() { return new DirectExchange(EXCHANGE_NAME, true, false); }

    @Bean
    public Binding commentBinding(Queue commentQueue, DirectExchange luntanExchange) {
        return BindingBuilder.bind(commentQueue).to(luntanExchange).with(ROUTING_COMMENT);
    }

    @Bean
    public Binding activityBinding(Queue activityQueue, DirectExchange luntanExchange) {
        return BindingBuilder.bind(activityQueue).to(luntanExchange).with(ROUTING_ACTIVITY);
    }
}