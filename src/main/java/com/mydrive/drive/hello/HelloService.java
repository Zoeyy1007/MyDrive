
/*
 * PHASE 0 - HTTP EXERCISE: Service layer
 *
 * Package: com.mydrive.drive.hello
 * Type to create: public class HelloService
 * Spring annotation to research: @Service
 *
 * Functions to implement:
 *
 *   public String createGreeting()
 *     - Takes no arguments.
 *     - Returns a simple greeting such as "Hello from MyDrive!".
 *
 *   public String createGreetingFor(String name)
 *     - Receives a name from the controller.
 *     - Returns a personalized greeting.
 *
 * Responsibility:
 *   This class owns greeting behavior. It knows nothing about URLs, HTTP,
 *   JSON, or ResponseEntity.
 */
package com.mydrive.drive.hello;

import org.springframework.stereotype.Service;

@Service
public class HelloService {
    public String createGreeting(){
        return "Hello from MyDrive!";
    }

    public String createGreetingFor(String name){
        return "Hello, " + name;
    }
}