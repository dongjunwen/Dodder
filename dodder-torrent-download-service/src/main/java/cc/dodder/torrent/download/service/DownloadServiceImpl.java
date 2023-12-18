package cc.dodder.torrent.download.service;

import cc.dodder.common.entity.DownloadMsgInfo;
import cc.dodder.torrent.download.task.BlockingExecutor;
import cc.dodder.torrent.download.task.DownloadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.*;

/**
 * @author jerry
 * @since 2023/12/18 11:08
 */
@Service
public class DownloadServiceImpl implements DownloadService{

    private static final Logger logger= LoggerFactory.getLogger(DownloadServiceImpl.class);

    @Value("${download.num.thread}")
    private int nThreads;
    @Autowired
    private RedisTemplate redisTemplate;

    private BlockingExecutor blockingExecutor;
    @PostConstruct
    public void init() {
       final ExecutorService threadPoolExecutor = new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), Executors.defaultThreadFactory());
        //max task bound 5000
        blockingExecutor = new BlockingExecutor(threadPoolExecutor, 5000);
    }

    /**
     * 种子下载
     * @param downloadMsgInfo 下载信息
     */
    @Override
    public void downloadTorrent(DownloadMsgInfo downloadMsgInfo) {
        try{
            logger.info("种子丢入线程池,开始下载:{}",downloadMsgInfo);
            blockingExecutor.execute(new DownloadTask(downloadMsgInfo));
        }catch (Exception e){
            logger.error("种子丢入线程池,发生异常:{0}",e);
        }

    }
}
