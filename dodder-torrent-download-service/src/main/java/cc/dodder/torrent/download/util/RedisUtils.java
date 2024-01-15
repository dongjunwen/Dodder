package cc.dodder.torrent.download.util;

import org.springframework.data.redis.core.StringRedisTemplate;

import static cc.dodder.common.constant.TopicConstant.TORRENT_TOPIC;

/**
 * @author jerry
 * @since 2024/1/15 14:47
 */
public class RedisUtils {
    private StringRedisTemplate stringRedisTemplate;

    public RedisUtils(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void publishMessage(String message){
        stringRedisTemplate.convertAndSend(TORRENT_TOPIC,message);
    }
}
