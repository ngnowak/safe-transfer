package com.nn.safetransfer;

import com.nn.safetransfer.outbox.application.OutboxPublisherProperties;
import com.nn.safetransfer.outbox.application.ApplicationKafkaProperties;
import com.nn.safetransfer.transfer.application.TransferRiskProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableKafka
@EnableScheduling
@EnableConfigurationProperties({OutboxPublisherProperties.class, ApplicationKafkaProperties.class, TransferRiskProperties.class})
@SpringBootApplication
public class SafetransferApplication {

	public static void main(String[] args) {
		SpringApplication.run(SafetransferApplication.class, args);
	}

}
