package com.thefale.security;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
class BankAccountCryptoTest {
    BankAccountCrypto crypto(int seed) { byte[] key=new byte[32]; java.util.Arrays.fill(key,(byte)seed); return new BankAccountCrypto(new MockEnvironment().withProperty("BANK_ACCOUNT_ENCRYPTION_KEY",Base64.getEncoder().encodeToString(key))); }
    @Test void encryptsWithFreshNonceAndRejectsWrongKey() {
        var cipher=crypto(1); String first=cipher.encrypt("123456789001"), second=cipher.encrypt("123456789001");
        assertThat(first).startsWith("AESGCM1:").doesNotContain("123456789001").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("123456789001");
        assertThatThrownBy(()->crypto(2).decrypt(first)).isInstanceOf(IllegalStateException.class);
    }
    @Test void legacyValuesCanBeMigratedWithoutChangingAccount() {
        var cipher=crypto(1); assertThat(cipher.decrypt(cipher.encrypt("ENC_123456789001"))).isEqualTo("123456789001");
        assertThat(cipher.encrypt(null)).isNull();
    }
    @Test void productionCannotStartWithoutExternalKey() {
        assertThatThrownBy(()->new BankAccountCrypto(new MockEnvironment()).ensureKey()).isInstanceOf(IllegalStateException.class);
    }
}
