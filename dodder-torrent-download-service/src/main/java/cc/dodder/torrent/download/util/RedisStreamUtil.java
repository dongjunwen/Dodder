package cc.dodder.torrent.download.util;

import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * @author jerry
 * @since 2023/12/18 16:15
 */
public class RedisStreamUtil {
    private StringRedisTemplate stringRedisTemplate;

    public RedisStreamUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * @author: xx
     * @date: 2022/4/12 17:29
     * @description: 创建分组
     * streamName: 流名称
     * groupName: 分组名称
     * readOffset: 消息读取规则
     */
    public String addGroup(String streamName, String groupName, ReadOffset readOffset) {
        String group = null;
        //获取是否已有该名称 stream
        Long size = stringRedisTemplate.opsForStream().size(streamName);
        if (size == null || size < 1) {
            // 没有设置读取规则
            group = stringRedisTemplate.opsForStream().createGroup(streamName,readOffset, groupName);
        }
        if (size != null && size >= 1) {
            // 已有直接创建新的分组
            group = stringRedisTemplate.opsForStream().createGroup(streamName, groupName);
        }
        return group;
    }

    /**
     * @author: xx
     * @date: 2022/4/12 17:29
     * @description: 发消息
     * streamName: 流名称
     * map: 用来封装消息内容
     * RecordId: 返回消息id
     */
    public RecordId addMessage(String streamName, Map<Object, Object> map) {
        return stringRedisTemplate.opsForStream().add(streamName, map);
    }

    /**
     * @author: xx
     * @date: 2022/4/12 17:30
     * @description: 收消息 每次读取一条消息
     * streamName: 流名称
     * groupName: 分组名称
     * consumerName: 消费者名称 (没有会自动创建)
     * duration: 读不到消息的阻塞时间
     * readOffset: 消息读取规则
     *
     */
    public MapRecord<String, Object, Object> readOne(String streamName, String groupName, String consumerName, Duration duration, ReadOffset readOffset) {
        List<MapRecord<String, Object, Object>> recordList = stringRedisTemplate.opsForStream().read(
                // 创建消费者
                Consumer.from(groupName, consumerName),
                // 每次读取 1条消息
                StreamReadOptions.empty().count(1).block(duration),
                StreamOffset.create(streamName, readOffset)
        );
        if (recordList == null || recordList.isEmpty()) {
            return null;
        }
        return recordList.get(0);

    }

    /**
     * @author: xx
     * @date: 2022/4/13 8:52
     * @description: 手动消息确认
     * streamName: 流名称
     * groupName: 分组名称
     * record: 消息
     */
    public Long acknowledge(String streamName,String groupName, MapRecord<String, Object, Object> record) {
        return stringRedisTemplate.opsForStream().acknowledge(streamName, groupName, record.getId());
    }

    /**
     * @author: xx
     * @date: 2022/4/13 9:10
     * @description: 删除消息
     * streamName: 流名称
     * record: 消息
     */
    public Long delete(String streamName, MapRecord<String, Object, Object> record) {
        return stringRedisTemplate.opsForStream().delete(streamName, record.getId());
    }
}
