package com.example.dormdelivery.push;

import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

public record SubscriptionInput(String endpoint, Map<String, String> keys) {
    public static void validateEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            boolean allowed = Set.of("fcm.googleapis.com", "updates.push.services.mozilla.com", "web.push.apple.com", "wns2-db5p.notify.windows.com").contains(host)
                    || (host != null && host.endsWith(".notify.windows.com"));
            if (endpoint.length() > 2048 || !"https".equals(uri.getScheme()) || !allowed || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getFragment() != null) throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw new IllegalArgumentException("지원하지 않는 푸시 endpoint입니다."); }
    }
    public void validate() {
        validateEndpoint(endpoint);
        try {
            byte[] key = Base64.getUrlDecoder().decode(keys.get("p256dh"));
            byte[] auth = Base64.getUrlDecoder().decode(keys.get("auth"));
            if (key.length != 65 || key[0] != 4 || auth.length != 16) throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw new IllegalArgumentException("구독 키가 올바르지 않습니다."); }
    }
}
