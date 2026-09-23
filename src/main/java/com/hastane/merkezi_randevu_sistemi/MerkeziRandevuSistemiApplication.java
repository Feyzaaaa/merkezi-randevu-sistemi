package com.hastane.merkezi_randevu_sistemi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

// @EnableAsync: e-posta gönderimi gibi işlemlerin arka planda (HTTP yanıtını bloklamadan) çalışmasını sağlar
@SpringBootApplication
@EnableAsync
public class MerkeziRandevuSistemiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MerkeziRandevuSistemiApplication.class, args);
	}

}
