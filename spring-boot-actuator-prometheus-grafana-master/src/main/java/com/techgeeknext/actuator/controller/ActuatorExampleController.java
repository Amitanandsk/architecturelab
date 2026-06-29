package com.techgeeknext.actuator.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController
public class ActuatorExampleController {

    @GetMapping("/")
    public String welcomeUser(@RequestParam(value = "name", defaultValue = "Guest") String name) {
        return "Hello TechGeekNext User: " + name + "!!";
    }

    @GetMapping("/testApi")
    public String testMetricApi(@RequestParam(value = "sleepTime", defaultValue = "0") Integer sleepTime) throws InterruptedException {
        if(sleepTime == 0) {
            sleepTime = new Random().nextInt(15);
        }
        TimeUnit.SECONDS.sleep(sleepTime);

        return "TechGeekNext - Successfully executed test Api.";
    }
}
