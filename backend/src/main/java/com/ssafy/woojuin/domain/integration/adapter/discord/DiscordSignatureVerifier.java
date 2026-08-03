package com.ssafy.woojuin.domain.integration.adapter.discord;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Discord Interaction 요청의 Ed25519 서명을 검증한다. */
@Component
public class DiscordSignatureVerifier {

    private static final byte[] ED25519_X509_PREFIX = HexFormat.of()
            .parseHex("302a300506032b6570032100");

    private final String publicKeyHex;

    public DiscordSignatureVerifier(
            @Value("${woojuin.integrations.discord.public-key:}") String publicKeyHex) {
        this.publicKeyHex = publicKeyHex == null ? "" : publicKeyHex.trim();
    }

    public boolean isConfigured() {
        return !publicKeyHex.isBlank();
    }

    public boolean verify(String timestamp, String signatureHex, byte[] body) {
        if (!isConfigured() || timestamp == null || signatureHex == null || body == null) {
            return false;
        }
        try {
            byte[] rawKey = HexFormat.of().parseHex(publicKeyHex);
            if (rawKey.length != 32) return false;

            byte[] encodedKey = new byte[ED25519_X509_PREFIX.length + rawKey.length];
            System.arraycopy(ED25519_X509_PREFIX, 0, encodedKey, 0, ED25519_X509_PREFIX.length);
            System.arraycopy(rawKey, 0, encodedKey, ED25519_X509_PREFIX.length, rawKey.length);
            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(encodedKey));

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(timestamp.getBytes(StandardCharsets.UTF_8));
            verifier.update(body);
            return verifier.verify(HexFormat.of().parseHex(signatureHex));
        } catch (Exception e) {
            return false;
        }
    }
}
