package com.webhookengine.app.delivery;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    static final String WEBHOOK_TOPIC = "webhook.delivery";

    // A topic is a channel of messages where our producers and consumers reads/writes messages from
    @Bean
    public NewTopic webhookDeliveryTopic() {
        return TopicBuilder.name("webhook.delivery")
                .partitions(10)
                .replicas(1)
                .build();
    }
}
