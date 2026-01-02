package com.l7guardian.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Main Entry Point.
 * * @SpringBootApplication triggers "Component Scanning".
 * It looks for all classes with @Component, @Service, @Controller, etc.,
 * in this package and any sub-packages (domain, infrastructure, application).
 */
@SpringBootApplication
public class L7GuardianProxyApplication {

    public static void main(String[] args) {
        // This command boots up the Embedded Tomcat server
        SpringApplication.run(L7GuardianProxyApplication.class, args);
    }
}