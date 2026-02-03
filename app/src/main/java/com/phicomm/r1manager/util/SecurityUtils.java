package com.phicomm.r1manager.util;

import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Common cryptographic utilities
 */
public class SecurityUtils {

    /**
     * Calculate SHA-256 hash
     */
    public static String getHash256(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes("UTF-8"));
        return bytesToHex(hash);
    }

    /**
     * Calculate HMAC-SHA512 signature
     */
    public static String getHmac512(String data, String key) throws Exception {
        Mac sha512_HMAC = Mac.getInstance("HmacSHA512");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA512");
        sha512_HMAC.init(secret_key);
        byte[] hash = sha512_HMAC.doFinal(data.getBytes("UTF-8"));
        return bytesToHex(hash);
    }

    /**
     * Convert byte array to Hex string
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
