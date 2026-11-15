package com.example.dormdelivery.push;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class PushSubscriptions {
    private final PushSubscriptionRepository repository;
    public PushSubscriptions(PushSubscriptionRepository repository) { this.repository=repository; }
    public static Long user(HttpSession session) {
        Object id = session.getAttribute("userId");
        if (!(id instanceof Long)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return (Long) id;
    }
    static String hash(String endpoint) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    @Transactional public void save(Long userId, SubscriptionInput input) {
        save(userId,input,null);
    }
    @Transactional public void save(Long userId, SubscriptionInput input,String deviceHash) {
        input.validate();
        PushSubscription sub=repository.findByEndpointHash(hash(input.endpoint())).orElseGet(PushSubscription::new);
        if (sub.getId()!=null && !sub.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 계정의 구독입니다. 알림을 해제한 후 다시 등록하세요.");
        sub.setUserId(userId); sub.setEndpoint(input.endpoint()); sub.setEndpointHash(hash(input.endpoint()));
        if(deviceHash!=null) sub.setDeviceHash(deviceHash);
        sub.setP256dh(input.keys().get("p256dh")); sub.setAuth(input.keys().get("auth")); repository.save(sub);
    }
    @Transactional public void remove(Long userId, String endpoint) {
        if (endpoint==null || endpoint.length()>2048) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        repository.findByEndpointHash(hash(endpoint)).ifPresent(sub -> {
            if (!sub.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            repository.delete(sub);
        });
    }
}
