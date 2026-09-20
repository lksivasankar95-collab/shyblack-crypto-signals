package com.shyblack.cryptosignals;

import com.shyblack.cryptosignals.config.BacktestingProperties;
import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.config.JwtProperties;
import com.shyblack.cryptosignals.config.LiveTradingProperties;
import com.shyblack.cryptosignals.config.MarketProperties;
import com.shyblack.cryptosignals.config.NewsProperties;
import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.config.SettingsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, MarketProperties.class, NewsProperties.class, SettingsProperties.class, PaperTradingProperties.class, LiveTradingProperties.class, FuturesTradingProperties.class, BacktestingProperties.class})
public class ShyblackBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShyblackBackendApplication.class, args);
	}
}
