package cc.dodder.torrent.download.task;

import cc.dodder.common.entity.DownloadMsgInfo;
import cc.dodder.torrent.download.service.DownloadService;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 种子见监听
 * @author jerry
 * @since 2024/1/15 14:39
 */
@Component
@Slf4j
public class TorrentListener implements MessageListener {
    @Resource
    private DownloadService downloadService;
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String recvMessage=new String(pattern);
        log.info("消费者收到的消息:{}",recvMessage);
        DownloadMsgInfo downloadMsgInfo= JSONObject.parseObject(recvMessage,DownloadMsgInfo.class);
        downloadService.downloadTorrent(downloadMsgInfo);
    }
}
