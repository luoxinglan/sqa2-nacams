package cn.sqa2.nacams;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NacamsApplication {
    public static void main(String[] args) { SpringApplication.run(NacamsApplication.class, args); }
}
