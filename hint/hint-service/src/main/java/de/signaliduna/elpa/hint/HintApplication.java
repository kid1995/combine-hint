package de.signaliduna.elpa.hint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HintApplication {

	public static void main(String[] args) {
		SpringApplication.run(HintApplication.class, args);
	}

}
