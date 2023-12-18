package cc.dodder.dhtserver.netty.config;

import cc.dodder.dhtserver.netty.handler.DhtServerHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/***
 * Netty 服务器配置
 *
 * @author: Mr.Xu
 * @create: 2019-02-15 14:50
 **/
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "netty")
public class NettyConfig implements ApplicationListener<ContextClosedEvent> {

	@Value("${netty.udp.port}")
	private int udpPort;
	@Value("${netty.so.rcvbuf}")
	private int rcvbuf;
	@Value("${netty.so.sndbuf}")
	private int sndbuf;

	private EventLoopGroup group;

	private ChannelFuture serverChannelFuture;


	@Autowired
	@Qualifier("dhtChannelInitializer")
	private DhtChannelInitializer dhtChannelInitializer;

	@Autowired
	private DhtServerHandler dhtServerHandler;

	@Bean(name = "serverBootstrap")
	public Bootstrap bootstrap() throws InterruptedException {
		group = group();
		Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(group)
				.channel(NioDatagramChannel.class)
				.handler(dhtChannelInitializer);
		Map<ChannelOption<?>, Object> udpChannelOptions = udpChannelOptions();
		Set<ChannelOption<?>> keySet = udpChannelOptions.keySet();
		for (@SuppressWarnings("rawtypes")
				ChannelOption option : keySet) {
			bootstrap.option(option, udpChannelOptions.get(option));
		}
		log.info("Starting dht server at " + udpPort());
		serverChannelFuture = bootstrap.bind(udpPort()).sync();
		serverChannelFuture.channel().closeFuture();
		dhtServerHandler.setServerChannelFuture(serverChannelFuture);
		return bootstrap;
	}

	@Bean(name = "group")
	public EventLoopGroup group() {
		return new NioEventLoopGroup();
	}

	@Bean(name = "udpSocketAddress")
	public InetSocketAddress udpPort() {
		return new InetSocketAddress(udpPort);
	}


	@Bean(name = "udpChannelOptions")
	public Map<ChannelOption<?>, Object> udpChannelOptions() {
		Map<ChannelOption<?>, Object> options = new HashMap<ChannelOption<?>, Object>();
		options.put(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
		options.put(ChannelOption.SO_BROADCAST, true);
		options.put(ChannelOption.SO_RCVBUF, rcvbuf);
		options.put(ChannelOption.SO_SNDBUF, sndbuf);
		return options;
	}

	@Override
	public void onApplicationEvent(ContextClosedEvent contextClosedEvent) {
		if(contextClosedEvent.getApplicationContext().getParent() == null) {
			group.shutdownGracefully();
		}
	}
}
