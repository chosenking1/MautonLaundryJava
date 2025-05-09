package com.work.mautonlaundry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//import org.springframework.aot.hint.annotation.NativeHint;

//import org.springframework.nativex.hint.NativeHint;
//import org.springframework.nativex.hint.TypeHint;

@SpringBootApplication(proxyBeanMethods = false)
//@NativeHint(types = @TypeHint(types = MautonLaundryApplication.class))
public class MautonLaundryApplication {
    public static void main(String[] args) {
        SpringApplication.run(MautonLaundryApplication.class, args);
    }
}


