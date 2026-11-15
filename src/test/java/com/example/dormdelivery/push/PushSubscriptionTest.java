package com.example.dormdelivery.push;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PushSubscriptionTest {
    @Test void rejectsInternalOrUntrustedEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> SubscriptionInput.validateEndpoint("https://localhost/push"));
        assertThrows(IllegalArgumentException.class, () -> SubscriptionInput.validateEndpoint("https://fcm.googleapis.com.evil.test/push"));
        assertThrows(IllegalArgumentException.class, () -> SubscriptionInput.validateEndpoint("http://fcm.googleapis.com/push"));
        assertThrows(IllegalArgumentException.class, () -> SubscriptionInput.validateEndpoint("https://fcm.googleapis.com:8443/push"));
    }
    @Test void acceptsSupportedBrowserPushProviders() {
        assertDoesNotThrow(() -> SubscriptionInput.validateEndpoint("https://fcm.googleapis.com/fcm/send/example"));
        assertDoesNotThrow(() -> SubscriptionInput.validateEndpoint("https://updates.push.services.mozilla.com/wpush/v2/example"));
        assertDoesNotThrow(() -> SubscriptionInput.validateEndpoint("https://web.push.apple.com/example"));
    }
}
