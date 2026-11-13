package com.thefale.service;

import com.thefale.entity.WalletTransaction;
import jakarta.persistence.PrePersist;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.Year;
import java.time.ZoneId;
import java.util.Locale;

/** Allocate once at creation; updates, retries and payout settlement retain the saved number. */
@Component
@RequiredArgsConstructor
public class WalletDocumentNumberListener {
    private final JdbcTemplate jdbc;

    @PrePersist
    public void assignNumber(WalletTransaction transaction) {
        Long serial = jdbc.queryForObject("SELECT nextval('wallet_document_number_seq')", Long.class);
        if (serial == null) throw new IllegalStateException("Document number could not be allocated.");
        String number = String.format(Locale.ROOT, "INV-%d-%06d", Year.now(ZoneId.of("Asia/Kolkata")).getValue(), serial);
        transaction.setDocumentNumber(number);
        transaction.setInvoiceId(number);
    }
}
