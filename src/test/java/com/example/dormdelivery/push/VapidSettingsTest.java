package com.example.dormdelivery.push;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VapidSettingsTest {
    @Test void browserReceivesNormalizedPublicKey() {
        var sender=org.mockito.Mockito.mock(WebPushSender.class);
        org.mockito.Mockito.when(sender.available()).thenReturn(true);
        var controller=new PushController(null,"  public-key\n",sender,null);
        var session=new org.springframework.mock.web.MockHttpSession();session.setAttribute("userId",1L);
        assertEquals("public-key",controller.key(session).get("publicKey"));
    }
    @Test void reportsMissingEnvironmentVariablesWithoutValues() {
        assertEquals("VAPID_PUBLIC_KEY_MISSING",new WebPushSender(true,"","","").initializationStatus());
        assertEquals("VAPID_PRIVATE_KEY_MISSING",new WebPushSender(true,"placeholder","","").initializationStatus());
        assertEquals("VAPID_SUBJECT_MISSING",new WebPushSender(true,"placeholder","placeholder","").initializationStatus());
    }
    @Test void reportsInvalidContactAndKeyFormatsSeparately() {
        assertEquals("VAPID_SUBJECT_INVALID",new WebPushSender(true,"placeholder","placeholder","not-a-contact").initializationStatus());
        assertEquals("VAPID_PUBLIC_KEY_INVALID",new WebPushSender(true,"%%%","placeholder","test@example.com").initializationStatus());
        assertEquals("DISABLED",new WebPushSender(false,"","","").initializationStatus());
    }
    @Test void redactsSettingsInObjectString() {
        String text=new VapidSettings("public-marker","private-marker","contact-marker").toString();
        assertFalse(text.contains("private-marker"));assertFalse(text.contains("contact-marker"));
    }
}
