package cc.dodder.dhtserver.netty.config;

import cc.dodder.dhtserver.netty.handler.DhtServerHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DatagramChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier("dhtChannelInitializer")
public class DhtChannelInitializer extends ChannelInitializer<DatagramChannel> {

	@Autowired
	private DhtServerHandler dhtServerHandler;

	@Override
	protected void initChannel(DatagramChannel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();
		pipeline.addLast("handler", dhtServerHandler);
	}
}
