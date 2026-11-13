package com.thefale.service;

import com.thefale.dto.ShiftCheckInRequest;
import com.thefale.exception.BadRequestException;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Base64;
import javax.imageio.ImageIO;

public final class ShiftAttendanceValidator {
    private ShiftAttendanceValidator() {}
    public static void validate(ShiftCheckInRequest r, Instant now) {
        if (r == null || r.getLatitude() == null || r.getLongitude() == null || r.getAccuracy() == null
                || !Double.isFinite(r.getLatitude()) || !Double.isFinite(r.getLongitude()) || !Double.isFinite(r.getAccuracy())
                || Math.abs(r.getLatitude()) > 90 || Math.abs(r.getLongitude()) > 180 || r.getAccuracy() < 0)
            throw new BadRequestException("Capture your current location before checking in.");
        if (!fresh(r.getLocationCapturedAt(), now) || !fresh(r.getSelfieCapturedAt(), now))
            throw new BadRequestException("Location and selfie must be captured within the last two minutes. Please recapture them.");
        String image = r.getSelfieDataUrl();
        if (image == null || image.length() > 450000 || !image.startsWith("data:image/jpeg;base64,"))
            throw new BadRequestException("A live JPEG selfie is required (maximum 300 KB).");
        try {
            byte[] bytes = Base64.getDecoder().decode(image.substring(23));
            if (bytes.length > 300000) throw new IllegalArgumentException();
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException();
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    int w = reader.getWidth(0), h = reader.getHeight(0);
                    if (!"JPEG".equalsIgnoreCase(reader.getFormatName()) || w < 160 || h < 160 || w > 2048 || h > 2048
                            || reader.read(0) == null) throw new IllegalArgumentException();
                } finally { reader.dispose(); }
            }
        } catch (Exception e) { throw new BadRequestException("The selfie is invalid. Please take another photo."); }
    }
    private static boolean fresh(Instant value, Instant now) {
        return value != null && !value.isBefore(now.minusSeconds(120)) && !value.isAfter(now.plusSeconds(30));
    }
}
