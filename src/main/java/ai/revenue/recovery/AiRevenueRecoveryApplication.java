package ai.revenue.recovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiRevenueRecoveryApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiRevenueRecoveryApplication.class, args);
	}

}
