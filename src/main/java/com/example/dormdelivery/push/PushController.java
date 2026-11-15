package com.example.dormdelivery.push;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/push")
public class PushController {
    private final PushSubscriptions subscriptions;
    private final String publicKey;
    private final boolean enabled;
    private final PushDevices devices;
    public PushController(PushSubscriptions subscriptions, @Value("${push.public-key:}") String key, WebPushSender sender, PushDevices devices) {
        this.devices=devices;
        this.subscriptions=subscriptions; this.publicKey=key; this.enabled=sender.available();
    }
    @GetMapping("/public-key") public Map<String,Object> key(HttpSession session) {
        PushSubscriptions.user(session); return Map.of("enabled",enabled,"publicKey",enabled?publicKey:"");
    }
    @PostMapping("/subscriptions") public ResponseEntity<Void> save(@RequestBody SubscriptionInput input,HttpSession session,jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response) {
        Long user=PushSubscriptions.user(session);
        if(!enabled) return ResponseEntity.status(503).build();
        try { subscriptions.save(user,input,devices.deviceHash(request,response)); } catch(IllegalArgumentException e) { return ResponseEntity.badRequest().build(); }
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/subscriptions") public ResponseEntity<Void> remove(@RequestBody Map<String,String> input,HttpSession session) {
        subscriptions.remove(PushSubscriptions.user(session),input.get("endpoint")); return ResponseEntity.noContent().build();
    }
}
