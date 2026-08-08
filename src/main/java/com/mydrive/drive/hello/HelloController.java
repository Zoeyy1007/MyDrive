
package com.mydrive.drive.hello;

import com.mydrive.drive.hello.dto.HelloResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hello")
public class HelloController{
    private final HelloService HelloService;
    public HelloController(HelloService HelloService){
        this.HelloService = HelloService;
    }

    @GetMapping
    public HelloResponse sayHello(){
        String message = HelloService.createGreeting();
        return new HelloResponse(message);
    }

    @GetMapping("/{name}")
    public HelloResponse sayHelloTo(@PathVariable String name){
        String message = HelloService.createGreetingFor(name);
        return new HelloResponse(message);
    }
}