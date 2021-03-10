package com.redisstream.kickstart.producer;

import com.redisstream.kickstart.config.ApplicationConfig;
import com.redisstream.kickstart.constant.Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.NumberUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Slf4j
@Component
public class StreamProducer {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ApplicationConfig config;

    public void produceNumbers() {
        Random random = new Random();
        long i = 0L;
        while (true) {
            int number = random.nextInt(2000);
            Map<String, String> fields = new HashMap<>();
            fields.put(Constant.NUMBER_KEY, String.valueOf(number));
            StringRecord record = StreamRecords.string(fields).withStreamKey(config.getOddEvenStream());
            redisTemplate.opsForStream().add(record);
            log.info("Message has been published : {}", number);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("Thread error:", e);
            }
        }
    }


}
