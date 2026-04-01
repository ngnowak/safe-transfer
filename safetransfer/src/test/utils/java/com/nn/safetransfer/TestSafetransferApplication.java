package com.nn.safetransfer;

import org.springframework.boot.SpringApplication;

public class TestSafetransferApplication {

	public static void main(String[] args) {
		SpringApplication.from(SafetransferApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
