package com.tripmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// 스케줄은 SSE ping(TripEvents) 에 쓴다
@SpringBootApplication
@EnableScheduling
public class TripMateApplication {

	public static void main(String[] args) {
		SpringApplication.run(TripMateApplication.class, args);
	}

}
