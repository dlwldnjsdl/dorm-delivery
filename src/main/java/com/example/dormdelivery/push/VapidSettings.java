package com.example.dormdelivery.push;

import java.net.URI;
import java.util.Base64;

/** Validation errors carry fixed codes only; never include key or contact input. */
record VapidSettings(String publicKey, String privateKey, String subject) {
    static final class Invalid extends IllegalArgumentException {
        Invalid(String code) { super(code); }
    }

    static VapidSettings parse(String publicKey, String privateKey, String subject) {
        String publicValue = clean(publicKey);
        String privateValue = clean(privateKey);
        String contact = clean(subject);
        if (publicValue.isEmpty()) throw new Invalid("VAPID_PUBLIC_KEY_MISSING");
        if (privateValue.isEmpty()) throw new Invalid("VAPID_PRIVATE_KEY_MISSING");
        if (contact.isEmpty()) throw new Invalid("VAPID_SUBJECT_MISSING");
        if (email(contact)) contact = "mailto:" + contact;
        try {
            URI uri = URI.create(contact);
            if ("mailto".equalsIgnoreCase(uri.getScheme()) && email(uri.getSchemeSpecificPart())) {
                contact = "mailto:" + uri.getSchemeSpecificPart();
            } else if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null) {
                contact = uri.toString();
            } else throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw new Invalid("VAPID_SUBJECT_INVALID"); }
        byte[] publicBytes = decode(publicValue, "VAPID_PUBLIC_KEY_INVALID");
        byte[] privateBytes = decode(privateValue, "VAPID_PRIVATE_KEY_INVALID");
        if (publicBytes.length != 65 || publicBytes[0] != 4) throw new Invalid("VAPID_PUBLIC_KEY_INVALID");
        if (privateBytes.length != 32) throw new Invalid("VAPID_PRIVATE_KEY_INVALID");
        return new VapidSettings(publicValue, privateValue, contact);
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
    private static boolean email(String value) { return value.matches("[^\\s@:/?#]+@[^\\s@:/?#]+\\.[^\\s@:/?#]+"); }
    private static byte[] decode(String value, String code) {
        try { return Base64.getUrlDecoder().decode(value); }
        catch (IllegalArgumentException e) { throw new Invalid(code); }
    }

    @Override public String toString() { return "VapidSettings[redacted]"; }
}
