package com.shyblack.cryptosignals;

import com.shyblack.cryptosignals.config.JwtProperties;
import com.shyblack.cryptosignals.config.MarketProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, MarketProperties.class})
public class ShyblackBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShyblackBackendApplication.class, args);
	}
}
