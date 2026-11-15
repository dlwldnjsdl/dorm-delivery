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
    private final String initializationStatus;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebPushSender.class);
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
        String status="DISABLED";
        if(enabled) {
            try {
                VapidSettings settings=VapidSettings.parse(publicKey,privateKey,subject);
                Security.addProvider(new BouncyCastleProvider());
                var privateEc=(org.bouncycastle.jce.interfaces.ECPrivateKey)Utils.loadPrivateKey(settings.privateKey());
                byte[] derived=privateEc.getParameters().getG().multiply(privateEc.getD()).getEncoded(false);
                if(!java.security.MessageDigest.isEqual(derived,java.util.Base64.getUrlDecoder().decode(settings.publicKey()))) throw new VapidSettings.Invalid("VAPID_KEY_PAIR_MISMATCH");
                configured=new EncryptedPushService(settings.publicKey(),settings.privateKey(),settings.subject());
                status="READY";
                log.info("Web Push 초기화 완료 [READY]");
            } catch(VapidSettings.Invalid e) {
                status=e.getMessage();
                log.warn("Web Push 발송 비활성화 [{}]. 키 값은 출력하지 않습니다.",status);
            } catch(Exception e) {
                status="VAPID_CRYPTO_INITIALIZATION_FAILED";
                log.warn("Web Push 발송 비활성화 [{}; {}]. 키 값과 예외 메시지는 출력하지 않습니다.",status,e.getClass().getSimpleName());
            }
        }
        service=configured;
        initializationStatus=status;
    }
    public boolean available() { return service!=null; }
    public String initializationStatus() { return initializationStatus; }
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
