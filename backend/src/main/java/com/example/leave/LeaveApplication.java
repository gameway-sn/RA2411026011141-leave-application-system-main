package com.example.leave;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. On startup the BPMN process and the approval form in
 * src/main/resources/bpmn are deployed to Camunda automatically.
 */
@SpringBootApplication
@Deployment(resources = {
        "classpath*:/bpmn/**/*.bpmn",
        "classpath*:/bpmn/**/*.form"
})
public class LeaveApplication {
    public static void main(String[] args) {
        SpringApplication.run(LeaveApplication.class, args);
    }
}
