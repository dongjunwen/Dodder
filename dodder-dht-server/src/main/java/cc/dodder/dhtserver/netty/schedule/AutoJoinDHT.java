package cc.dodder.dhtserver.netty.schedule;

import cc.dodder.dhtserver.netty.handler.DhtServerHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/***
 * 定时检测本地节点数并自动加入 DHT 网络
 *
 * @author Mr.Xu
 * @date 2019-02-16 22:04
 **/
@Slf4j
@Component
public class AutoJoinDHT {

	@Autowired
	private DhtServerHandler dhtServerHandler;

	@Scheduled(fixedDelay = 30 * 1000, initialDelay = 3 * 1000)
	public void doJob() {
		if (dhtServerHandler.getNodeQueues().isEmpty()) {
			log.info("本地 DHT 节点数为0，自动重新加入 DHT 网络中...");
			dhtServerHandler.joinDHT();
		}
	}
}
