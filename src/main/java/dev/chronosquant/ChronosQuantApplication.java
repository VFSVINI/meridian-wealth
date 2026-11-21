package dev.chronosquant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChronosQuantApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChronosQuantApplication.class, args);
    }
}
