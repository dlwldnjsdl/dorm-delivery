package com.example.dormdelivery.push;

import jakarta.servlet.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.*;

/** Random HttpOnly device capability permits cleanup after session expiry without trusting a userId. */
@Service
public class PushDevices {
    private final PushSubscriptionRepository repository;
    public PushDevices(PushSubscriptionRepository repository) { this.repository=repository; }
    public String deviceHash(HttpServletRequest request,HttpServletResponse response) {
        String value=token(request);
        if(value==null) {
            byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
            value=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            Cookie cookie=new Cookie("push_device",value);cookie.setHttpOnly(true);cookie.setSecure(request.isSecure());cookie.setPath("/");cookie.setMaxAge(31536000);cookie.setAttribute("SameSite","Strict");response.addCookie(cookie);
        }
        return PushSubscriptions.hash(value);
    }
    private String token(HttpServletRequest request) {
        if(request.getCookies()!=null) for(Cookie cookie:request.getCookies()) if("push_device".equals(cookie.getName()) && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) return cookie.getValue();
        return null;
    }
    @Transactional public void logout(HttpServletRequest request) {
        String token=token(request);
        if(token!=null) repository.deleteAll(repository.findByDeviceHash(PushSubscriptions.hash(token)));
    }
    @Transactional public void reconcile(HttpServletRequest request,Long userId) {
        String token=token(request);
        if(token!=null) repository.findByDeviceHash(PushSubscriptions.hash(token)).stream().filter(s -> !s.getUserId().equals(userId)).forEach(repository::delete);
    }
}
