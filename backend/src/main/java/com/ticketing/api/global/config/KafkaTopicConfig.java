package com.ticketing.api.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String ENTRY_REQUESTED = "entry.requested";
    public static final String ENTRY_GRANTED = "entry.granted";
    public static final String SEAT_CONFIRMED = "seat.confirmed";
    public static final String SEAT_RELEASED = "seat.released";

    @Bean
    public NewTopic entryRequestedTopic() {
        return TopicBuilder.name(ENTRY_REQUESTED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic entryGrantedTopic() {
        return TopicBuilder.name(ENTRY_GRANTED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic seatConfirmedTopic() {
        return TopicBuilder.name(SEAT_CONFIRMED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic seatReleasedTopic() {
        return TopicBuilder.name(SEAT_RELEASED).partitions(3).replicas(1).build();
    }
}
