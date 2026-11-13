package com.thefale.service;
import java.time.Year;
import java.util.UUID;
public final class DocumentReferences {
    private DocumentReferences() {}
    public static String next(String prefix) { return prefix + "-" + Year.now().getValue() + "-" + UUID.randomUUID(); }
    public static String settlement(UUID claimId) { return "SET-" + claimId; }
    public static String payout(UUID claimId) { return "PAY-" + claimId; }
}
