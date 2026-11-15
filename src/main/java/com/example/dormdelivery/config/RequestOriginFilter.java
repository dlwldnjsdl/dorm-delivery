package com.example.dormdelivery.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.util.*;

@Component
public class RequestOriginFilter extends OncePerRequestFilter {
    private final Set<String> allowed;
    public RequestOriginFilter(@Value("${app.allowed-origins:http://localhost:5173,http://localhost:8080}") String origins) { allowed=Set.copyOf(Arrays.asList(origins.split(","))); }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String origin=request.getHeader("Origin");
        String own=request.getScheme()+"://"+request.getHeader("Host");
        if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod()) && origin!=null && !origin.equals(own) && !allowed.contains(origin)) {
            response.sendError(403); return;
        }
        chain.doFilter(request,response);
    }
}
