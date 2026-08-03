package com.ssafy.woojuin.domain.integration.adapter.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class DiscordSignatureVerifierTest {

    @Test
    void verifiesTimestampAndBodyWithEd25519PublicKey() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] encoded = pair.getPublic().getEncoded();
        String rawPublicKey = HexFormat.of().formatHex(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
        DiscordSignatureVerifier verifier = new DiscordSignatureVerifier(rawPublicKey);
        String timestamp = "1720000000";
        byte[] body = "{\"type\":1}".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(timestamp.getBytes(StandardCharsets.UTF_8));
        signer.update(body);
        String signature = HexFormat.of().formatHex(signer.sign());

        assertThat(verifier.verify(timestamp, signature, body)).isTrue();
        assertThat(verifier.verify(timestamp, signature, "{}".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(verifier.verify(timestamp, "00", body)).isFalse();
    }
}
