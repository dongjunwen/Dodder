package cc.dodder.torrent.download.task;

import cc.dodder.common.entity.DownloadMsgInfo;
import cc.dodder.torrent.download.service.DownloadService;
import cc.dodder.torrent.download.util.RedisStreamUtil;
import cc.dodder.torrent.download.util.SpringContextUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 下载消费者
 * @author jerry
 * @since 2023/12/18 16:22
 */
@Slf4j
@Component
public class DownloadConsumer implements ApplicationRunner {


    @Autowired
    private DownloadService downloadService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        StringRedisTemplate redisTemplate = (StringRedisTemplate)SpringContextUtil.getBean(StringRedisTemplate.class);
        long countNum=1;
        while (true){
            try{
                log.info("第{}次种子下载消费",countNum);
                RedisStreamUtil streamUtil = new RedisStreamUtil(redisTemplate);
                MapRecord<String, Object, Object> record = streamUtil.readOne("downloadStream", "downloadGroup", "DownloadConsumer", Duration.ofSeconds(2), ReadOffset.lastConsumed());
                if (record == null) {
                    log.info("种子下载消费,没有最新消息");
                    TimeUnit.SECONDS.sleep(50);
                    continue;
                }
                Map<Object, Object> map = record.getValue();
                for(Object keySet:map.keySet()){
                    String  downloadMsgInfoStr= (String) map.get(keySet);
                    DownloadMsgInfo downloadMsgInfo= JSON.parseObject(downloadMsgInfoStr,DownloadMsgInfo.class);
                    log.info("种子下载消费,最新消息:{}",downloadMsgInfo);
                    downloadService.downloadTorrent(downloadMsgInfo);
                    streamUtil.acknowledge("downloadStream","downloadGroup",record);
                    streamUtil.delete("downloadStream",record);
                }
                countNum++;
                if(countNum%1000==0){
                    log.info("种子下载消费,达到1000条,休息3s");
                    TimeUnit.SECONDS.sleep(3);
                    countNum=1;
                }else {
                   TimeUnit.SECONDS.sleep(1);
                }
            }catch (Exception e){
             log.info("种子下载消费发生异常:{0}",e);
                TimeUnit.SECONDS.sleep(50);
            }
        }
    }
}
