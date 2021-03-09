package com.redisstream.kickstart.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Validated
@Configuration
@Data
public class ApplicationConfig {

    @Value("${odd-list-key}")
    private String oddListKey;
    @Value("${even-list-key}")
    private String evenListKey;
    @Value("${odd-even-stream}")
    private String oddEvenStream;
    @Value("${consumer-group-name}")
    private String consumerGroupName;
    @Value("${redis-host}")
    private String redisHost;
    @Value("${redis-port}")
    private int redisPort;
    @Value("${record-cache-key}")
    private String recordCacheKey;
    @Value("${stream-poll-timeout}")
    private long streamPollTimeout;

    private String consumerName;
    @Value("${failure-list-key}")
    private String failureListKey;

    @PostConstruct
    public void setConsumerName() throws UnknownHostException {
        consumerName = InetAddress.getLocalHost().getHostName() + UUID.randomUUID();
    }
}
