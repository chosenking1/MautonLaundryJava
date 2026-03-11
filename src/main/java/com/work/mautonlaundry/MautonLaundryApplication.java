package com.work.mautonlaundry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication(proxyBeanMethods = false)
@EnableScheduling
public class MautonLaundryApplication {
    public static void main(String[] args) {
        SpringApplication.run(MautonLaundryApplication.class, args);
    }
}

