package com.nn.safetransfer;

import com.nn.safetransfer.outbox.application.OutboxPublisherProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties(OutboxPublisherProperties.class)
@SpringBootApplication
public class SafetransferApplication {

	public static void main(String[] args) {
		SpringApplication.run(SafetransferApplication.class, args);
	}

}
