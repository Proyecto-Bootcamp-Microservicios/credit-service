package com.bootcamp.ntt.credit_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
public class CreditServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CreditServiceApplication.class, args);
	}

}
