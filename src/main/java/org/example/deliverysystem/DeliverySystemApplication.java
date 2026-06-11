package org.example.deliverysystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example", "org.example"})
public class DeliverySystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliverySystemApplication.class, args);
    }
}