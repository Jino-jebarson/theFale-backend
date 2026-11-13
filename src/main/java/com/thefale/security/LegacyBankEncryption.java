package com.thefale.security;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
@Profile("!ally-transport-test")
@RequiredArgsConstructor
public class LegacyBankEncryption implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final BankAccountCrypto crypto;
    @Override @Transactional public void run(ApplicationArguments args) {
        crypto.ensureKey();
        for (String table : new String[]{"candidate_payout_methods", "hospital_bank_accounts"}) {
            jdbc.query("SELECT id, account_number_encrypted FROM " + table + " WHERE account_number_encrypted IS NOT NULL AND account_number_encrypted NOT LIKE 'AESGCM1:%' FOR UPDATE", rs -> {
                String original = rs.getString(2);
                jdbc.update("UPDATE " + table + " SET account_number_encrypted = ? WHERE id = ? AND account_number_encrypted = ?", crypto.encrypt(original), rs.getObject(1), original);
            });
        }
    }
}
