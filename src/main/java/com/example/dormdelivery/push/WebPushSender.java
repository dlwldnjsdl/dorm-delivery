package com.example.dormdelivery.push;

import nl.martijndwars.webpush.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.security.Security;

@Component
public class WebPushSender {
    private final EncryptedPushService service;
    private static class EncryptedPushService extends PushService {
        EncryptedPushService(String publicKey,String privateKey,String subject) throws Exception { super(publicKey,privateKey,subject); }
        nl.martijndwars.webpush.HttpRequest request(Notification notification) throws Exception { return prepareRequest(notification,Encoding.AES128GCM); }
    }
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public WebPushSender(@Value("${push.enabled:false}") boolean enabled,
                         @Value("${push.public-key:}") String publicKey,
                         @Value("${push.private-key:}") String privateKey,
                         @Value("${push.subject:}") String subject) {
        EncryptedPushService configured=null;
        if(enabled) {
            try {
                if(subject.isBlank() || !(subject.startsWith("mailto:") || subject.startsWith("https://"))) throw new IllegalArgumentException();
                Security.addProvider(new BouncyCastleProvider());
                configured=new EncryptedPushService(publicKey,privateKey,subject);
            } catch(Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Web Push 설정이 유효하지 않아 발송을 비활성화했습니다."); }
        }
        service=configured;
    }
    public boolean available() { return service!=null; }
    public int send(PushSubscription subscription, byte[] payload) throws Exception {
        if(service==null) return 503;
        var prepared=encryptedRequest(subscription,payload);
        var builder=java.net.http.HttpRequest.newBuilder(URI.create(prepared.getUrl())).timeout(Duration.ofSeconds(10));
        prepared.getHeaders().forEach(builder::header);
        return client.send(builder.POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(prepared.getBody())).build(),HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    nl.martijndwars.webpush.HttpRequest encryptedRequest(PushSubscription subscription,byte[] payload) throws Exception {
        SubscriptionInput.validateEndpoint(subscription.getEndpoint());
        Notification notification=new Notification(subscription.getEndpoint(),subscription.getP256dh(),subscription.getAuth(),payload,300);
        return service.request(notification);
    }
}
