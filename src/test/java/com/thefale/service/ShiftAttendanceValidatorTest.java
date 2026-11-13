package com.thefale.service;

import com.thefale.dto.ShiftCheckInRequest;
import com.thefale.exception.BadRequestException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShiftAttendanceValidatorTest {
    private final Instant now = Instant.now();
    private ShiftCheckInRequest request() {
        ShiftCheckInRequest request = new ShiftCheckInRequest();
        request.setLatitude(18.52); request.setLongitude(73.85); request.setAccuracy(10.0);
        request.setLocationCapturedAt(now); request.setSelfieCapturedAt(now);
        request.setSelfieDataUrl("data:image/jpeg;base64,bm90LWEtcGhvdG8=");
        return request;
    }
    @Test void rejectsStaleOrFutureEvidence() {
        ShiftCheckInRequest request = request();
        request.setLocationCapturedAt(now.minusSeconds(121));
        assertThatThrownBy(() -> ShiftAttendanceValidator.validate(request, now)).isInstanceOf(BadRequestException.class).hasMessageContaining("two minutes");
        request.setLocationCapturedAt(now); request.setSelfieCapturedAt(now.plusSeconds(31));
        assertThatThrownBy(() -> ShiftAttendanceValidator.validate(request, now)).isInstanceOf(BadRequestException.class).hasMessageContaining("two minutes");
    }
    @Test void rejectsNonFiniteOrOutOfRangeCoordinates() {
        ShiftCheckInRequest request = request(); request.setLatitude(Double.NaN);
        assertThatThrownBy(() -> ShiftAttendanceValidator.validate(request, now)).isInstanceOf(BadRequestException.class).hasMessageContaining("location");
        request.setLatitude(91.0);
        assertThatThrownBy(() -> ShiftAttendanceValidator.validate(request, now)).isInstanceOf(BadRequestException.class).hasMessageContaining("location");
    }
    @Test void rejectsTextDisguisedAsAJpeg() {
        assertThatThrownBy(() -> ShiftAttendanceValidator.validate(request(), now)).isInstanceOf(BadRequestException.class).hasMessageContaining("selfie is invalid");
    }
}
