package com.work.mautonlaundry.config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
    public class AppConfig {



        @Bean
        public ModelMapper modelMapper() {
            return new ModelMapper();
        }
    }


