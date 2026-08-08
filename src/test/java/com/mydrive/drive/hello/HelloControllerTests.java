
package com.mydrive.drive.hello;

import com.mydrive.drive.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
//import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(HelloController.class)
@Import(SecurityConfig.class)
class HelloControllerTests{
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HelloService helloService;

    @Test
    void sayHelloReturnsDefaultGreeting() throws Exception{
        when(helloService.createGreeting()).thenReturn("Hello from MyDrive!");

        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello from MyDrive!"));
    }

    @Test
    void sayHelloToReturnPersonalizedGreeting() throws Exception{
        when(helloService.createGreetingFor("Zoey")).thenReturn("Hello, Zoey");

        mockMvc.perform(get("/api/hello/Zoey"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello, Zoey"));
    }
}
