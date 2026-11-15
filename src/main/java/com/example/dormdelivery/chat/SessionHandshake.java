package com.example.dormdelivery.chat;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;

public class SessionHandshake implements HandshakeInterceptor {
    public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes) {
        if(!(request instanceof ServletServerHttpRequest servlet)) return false;
        HttpSession session=servlet.getServletRequest().getSession(false);
        if(session==null || !(session.getAttribute("userId") instanceof Long)) return false;
        attributes.put("httpSession",session); return true;
    }
    public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception exception) {}
}
