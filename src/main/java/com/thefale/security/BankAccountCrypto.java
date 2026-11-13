package com.thefale.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class BankAccountCrypto {
    private static final String PREFIX = "AESGCM1:";
    private final Environment environment;
    private volatile byte[] key;
    public BankAccountCrypto(Environment environment) { this.environment = environment; }
    public synchronized void ensureKey() {
        if (key != null) return;
        try {
            String configured = environment.getProperty("BANK_ACCOUNT_ENCRYPTION_KEY", "");
            if (configured.isBlank()) {
                if (!environment.acceptsProfiles(Profiles.of("dev", "test")))
                    throw new IllegalStateException("BANK_ACCOUNT_ENCRYPTION_KEY must be configured for this environment.");
                Path path = Path.of(environment.getProperty("BANK_ACCOUNT_KEY_FILE", ".local-secrets/bank-account.key"));
                Files.createDirectories(path.toAbsolutePath().getParent());
                if (!Files.exists(path)) {
                    byte[] generated = new byte[32]; new SecureRandom().nextBytes(generated);
                    try { Files.writeString(path, Base64.getEncoder().encodeToString(generated), StandardOpenOption.CREATE_NEW); }
                    catch (FileAlreadyExistsException ignored) { }
                }
                configured = Files.readString(path).trim();
            }
            byte[] decoded = Base64.getDecoder().decode(configured);
            if (decoded.length != 32) throw new IllegalStateException("Bank encryption requires a 32-byte base64 key.");
            key = decoded;
        } catch (Exception e) { throw new IllegalStateException("Bank encryption key could not be initialized. Restore the configured key before starting.", e); }
    }
    public String encrypt(String value) {
        if (value == null || value.isBlank()) return value;
        if (value.startsWith(PREFIX)) return value;
        ensureKey();
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD("FALE_BANK_ACCOUNT_V1".getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(legacy(value).getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length); System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(result);
        } catch (Exception e) { throw new IllegalStateException("Bank account encryption failed.", e); }
    }
    public String decrypt(String value) {
        if (value == null || !value.startsWith(PREFIX)) return legacy(value);
        ensureKey();
        try {
            byte[] bytes = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, bytes, 0, 12));
            cipher.updateAAD("FALE_BANK_ACCOUNT_V1".getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Bank account could not be decrypted. Check the bank encryption key.", e); }
    }
    private static String legacy(String value) { return value != null && value.startsWith("ENC_") ? value.substring(4) : value; }
}
