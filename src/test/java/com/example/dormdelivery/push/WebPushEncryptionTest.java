package com.example.dormdelivery.push;
import org.junit.jupiter.api.Test;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WebPushEncryptionTest {
    @Test void generatesEncryptedPayloadAndVapidAuthorizationWithoutNetwork() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        var generator=KeyPairGenerator.getInstance("ECDH","BC");generator.initialize(new ECGenParameterSpec("prime256v1"));
        var pair=generator.generateKeyPair();
        String publicKey=Base64.getUrlEncoder().withoutPadding().encodeToString(((org.bouncycastle.jce.interfaces.ECPublicKey)pair.getPublic()).getQ().getEncoded(false));
        byte[] integer=((org.bouncycastle.jce.interfaces.ECPrivateKey)pair.getPrivate()).getD().toByteArray();
        byte[] scalar=new byte[32];System.arraycopy(integer,Math.max(0,integer.length-32),scalar,Math.max(0,32-integer.length),Math.min(32,integer.length));
        String privateKey=Base64.getUrlEncoder().withoutPadding().encodeToString(scalar);
        var sender=new WebPushSender(true,publicKey,privateKey,"mailto:test@example.com");assertTrue(sender.available());
        var subscription=new PushSubscription();subscription.setEndpoint("https://fcm.googleapis.com/fcm/send/test-only");subscription.setP256dh(publicKey);subscription.setAuth(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
        byte[] body="{\"body\":\"new message\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request=sender.encryptedRequest(subscription,body);
        assertFalse(Arrays.equals(body,request.getBody()));
        assertTrue(request.getBody().length>body.length);
        assertTrue(request.getHeaders().entrySet().stream().anyMatch(e -> e.getKey().equalsIgnoreCase("Authorization") && e.getValue().startsWith("vapid ")));
        assertTrue(request.getHeaders().entrySet().stream().anyMatch(e -> e.getKey().equalsIgnoreCase("Content-Encoding") && e.getValue().equals("aes128gcm")));
    }
}
