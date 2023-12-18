package cc.dodder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ComponentScan({"cc.dodder","io.netty"})
public class DhtServerApplication {


	public static void main(String[] args) {
		SpringApplication.run(DhtServerApplication.class, args);
	}

}

