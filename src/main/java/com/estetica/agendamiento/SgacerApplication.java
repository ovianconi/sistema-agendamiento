package com.estetica.agendamiento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@SpringBootApplication
@EnableScheduling
public class SgacerApplication {

    @GetMapping("/test")
    public String testEndpoint() {
        return "El backend está funcionando correctamente.";
    }

    public static void main(String[] args) {
        SpringApplication.run(SgacerApplication.class, args);
    }


}

