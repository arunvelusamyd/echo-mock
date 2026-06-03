package com.example.echomock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class EchoMockApplication extends SpringBootServletInitializer {

    /** Lets an external servlet container bootstrap the app from the WAR. */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(EchoMockApplication.class);
    }

    /** Still used when running the executable WAR with `java -jar`. */
    public static void main(String[] args) {
        SpringApplication.run(EchoMockApplication.class, args);
    }
}
