package com.example.dormdelivery.config;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/healthz") public Map<String,String> health() { return Map.of("status","ok"); }
}
