package com.example.dormdelivery.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import java.io.IOException;

/** Never expose the retired test page, including stale build resources. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LegacyTestPageFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        String path=UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        if(path.equals("/chat-test.html") || path.startsWith("/chat-test.html/")) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request,response);
    }
}
