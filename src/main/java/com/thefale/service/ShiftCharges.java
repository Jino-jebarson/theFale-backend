package com.thefale.service;

import com.thefale.entity.FacilityWallet;
import com.thefale.entity.LocumShift;
import com.thefale.exception.BadRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Currency is rounded per booking, then multiplied by openings, consistently across the lifecycle. */
public final class ShiftCharges {
    public static final BigDecimal GST_PERCENT = new BigDecimal("18");
    private ShiftCharges() {}
    public static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    public static BigDecimal percent(BigDecimal amount, BigDecimal rate) {
        return zero(amount).multiply(zero(rate)).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
    public static BigDecimal gst(BigDecimal fee, LocumShift shift) { return percent(fee, shift.getPlatformFeeGstPercent()); }
    public static BigDecimal reservedGst(LocumShift shift) { return zero(shift.getPlatformFeeGstAmount()); }
    public static void requireReserve(FacilityWallet wallet, BigDecimal amount) {
        if (amount.signum() < 0 || wallet.getLockedEscrowBalance().compareTo(amount) < 0)
            throw new BadRequestException("Escrow reserve mismatch. Contact support before settling or refunding this duty.");
    }
}
