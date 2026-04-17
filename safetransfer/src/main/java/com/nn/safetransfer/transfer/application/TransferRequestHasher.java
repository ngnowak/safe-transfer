package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component
public class TransferRequestHasher {
    private static final String SHA_256 = "SHA-256";

    public String hash(CreateTransferRequest request) {
        var canonicalRequest = "%s|%s|%s|%s|%s".formatted(
                request.sourceWalletId(),
                request.destinationWalletId(),
                request.amount().stripTrailingZeros().toPlainString(),
                request.currency(),
                request.reference() == null ? "" : request.reference()
        );

        try {
            var digest = MessageDigest.getInstance(SHA_256)
                    .digest(canonicalRequest.getBytes(UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("%s algorithm is not available".formatted(SHA_256), ex);
        }
    }

    private String toHex(byte[] bytes) {
        var hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append("%02x".formatted(value));
        }
        return hex.toString();
    }
}
