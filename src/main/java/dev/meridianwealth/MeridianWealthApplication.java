package dev.meridianwealth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MeridianWealthApplication {
    public static void main(String[] args) {
        SpringApplication.run(MeridianWealthApplication.class, args);
    }
}
