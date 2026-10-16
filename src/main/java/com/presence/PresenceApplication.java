/**
 * Purpose: Starts the Presence application.
 * Why it is used: This is the main entry point that tells Spring Boot to start the website.
 */
package com.presence;

import com.presence.config.PresenceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PresenceProperties.class)
public class PresenceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PresenceApplication.class, args);
    }
}
