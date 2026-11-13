package com.thefale.service;

import com.thefale.entity.LocumShift;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ShiftChargesTest {
    @Test void gstIsEighteenPercentOfFeeOnly() {
        BigDecimal fee = ShiftCharges.percent(new BigDecimal("3600"), new BigDecimal("8"));
        BigDecimal gst = ShiftCharges.percent(fee, ShiftCharges.GST_PERCENT);
        assertThat(fee).isEqualByComparingTo("288.00");
        assertThat(gst).isEqualByComparingTo("51.84");
        assertThat(new BigDecimal("3600").add(fee).add(gst)).isEqualByComparingTo("3939.84");
    }
    @Test void waiverHasNoGst() {
        assertThat(ShiftCharges.percent(BigDecimal.ZERO, ShiftCharges.GST_PERCENT)).isEqualByComparingTo("0");
    }
    @Test void historicalReservationsDoNotAcquireGst() {
        assertThat(ShiftCharges.gst(new BigDecimal("288"), LocumShift.builder().build())).isEqualByComparingTo("0");
    }
    @Test void roundsPerBookingToPaise() {
        BigDecimal fee = ShiftCharges.percent(new BigDecimal("1.05"), new BigDecimal("8"));
        assertThat(fee).isEqualByComparingTo("0.08");
        assertThat(ShiftCharges.percent(fee, ShiftCharges.GST_PERCENT)).isEqualByComparingTo("0.01");
    }
}
