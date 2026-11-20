package com.tripmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

// 스케줄은 SSE ping(TripEvents) 에 쓴다
@SpringBootApplication
@EnableScheduling
public class TripMateApplication {

	public static void main(String[] args) {
		// 날짜(D-day, 여행이 끝났나, 회비 걷는 중)는 한국 날짜로 센다. 배포 서버가 UTC 면 새벽 0~9시에 하루 어제로 셌다
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(TripMateApplication.class, args);
	}

}
