package com.insurancebilling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the simulated insurance billing platform.
 *
 * <p>The application is the system under test for every automation suite in this repository. It runs
 * against an in-memory H2 database so that a suite can be pointed at a freshly started instance
 * without any external infrastructure.
 */
@SpringBootApplication
public class BillingApplication {

  public static void main(String[] args) {
    SpringApplication.run(BillingApplication.class, args);
  }
}
