package com.redisstream.kickstart.consumer;

import com.redisstream.kickstart.config.ApplicationConfig;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandKeyword;
import io.lettuce.core.protocol.CommandType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;

import static com.redisstream.kickstart.constant.Constant.*;

@Slf4j
@Component
@EnableScheduling
public class StreamConsumer implements StreamListener<String, MapRecord<String, String, Object>>, InitializingBean,
        DisposableBean {


    @Resource
    ApplicationConfig config;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private StreamMessageListenerContainer<String, MapRecord<String, String, Object>> listenerContainer;
    private Subscription subscription;
    private String consumerName;
    private String consumerGroupName;
    private String streamName;


    @Override
    public void onMessage(MapRecord<String, String, Object> message) {
        //extract the number from the message
        try {
            String inputNumber = message.getValue().get(NUMBER_KEY).toString();
//            var number = (Integer)message.getValue().get(NUMBER_KEY);
            final int number = Integer.parseInt(inputNumber);
            if (number % 2 == 0) {
                redisTemplate.opsForList().rightPush(config.getEvenListKey(), number);
            } else {
                redisTemplate.opsForList().rightPush(config.getOddListKey(), number);
            }
            redisTemplate.opsForHash().put(config.getRecordCacheKey(), LAST_RESULT_HASH_KEY, inputNumber);
            redisTemplate.opsForHash().increment(config.getRecordCacheKey(), PROCESSED_HASH_KEY, 1);
            redisTemplate.opsForStream().acknowledge(config.getConsumerGroupName(), message);
            redisTemplate.opsForStream().delete(message);
            log.info("Message has been processed");
        } catch (Exception ex) {
            //log the exception and increment the number of errors count
            log.error("Failed to process the message: {} ", message.getValue().get(NUMBER_KEY), ex);
            redisTemplate.opsForHash().increment(config.getRecordCacheKey(), ERRORS_HASH_KEY, 1);
        }

    }

    @Override
    public void destroy() throws Exception {
        if (subscription != null) {
            subscription.cancel();
        }

        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //name for this consumer which will be registered with consumer group
        consumerName = config.getConsumerName();
        consumerGroupName = config.getConsumerGroupName();
        streamName = config.getOddEvenStream();

        try {
            //create consumer group for the stream
            // if stream does not exist it will create stream first then create consumer group
            if (!redisTemplate.hasKey(streamName)) {
                log.info("{} does not exist. Creating stream along with the consumer group", streamName);
                RedisAsyncCommands commands = (RedisAsyncCommands) redisTemplate.getConnectionFactory()
                        .getConnection().getNativeConnection();
                CommandArgs<String, String> args = new CommandArgs<>(StringCodec.UTF8)
                        .add(CommandKeyword.CREATE)
                        .add(streamName)
                        .add(consumerGroupName)
                        .add("0")
                        .add("MKSTREAM");
                commands.dispatch(CommandType.XGROUP, new StatusOutput<>(StringCodec.UTF8), args);
            } else {
                //creating consumer group
                redisTemplate.opsForStream().createGroup(streamName, ReadOffset.from("0"), consumerGroupName);
            }
        } catch (Exception ex) {
            log.info("Consumer group already present: {}", consumerGroupName);
        }


        this.listenerContainer = StreamMessageListenerContainer.create(redisTemplate.getConnectionFactory(),
                StreamMessageListenerContainer
                        .StreamMessageListenerContainerOptions.builder()
                        .hashKeySerializer(RedisSerializer.string())
                        .pollTimeout(Duration.ofMillis(config.getStreamPollTimeout()))
                        .build());

        this.subscription = listenerContainer.receive(
                Consumer.from(consumerGroupName, consumerName),
                StreamOffset.create(streamName, ReadOffset.lastConsumed()),
                this);

        subscription.await(Duration.ofSeconds(2));
        listenerContainer.start();
    }
}
