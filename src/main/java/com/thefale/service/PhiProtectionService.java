package com.thefale.service;

import com.thefale.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PhiProtectionService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:(?:\\+91|0)?[6-9]\\d{9})|\\b\\d{10,12}\\b");
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern HOSPITAL_ID_PATTERN = Pattern.compile("\\b(?:MRN|UHID|IPD|OPD|REG(?:ISTRATION)?)[-:\\s#]?\\d{3,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATIENT_IDENTIFIER_PATTERN = Pattern.compile("\\b(?:patient|pt\\.?|mr\\.?|mrs\\.?|ms\\.?)\\s+[A-Za-z]{2,}(?:\\s+[A-Za-z]{2,})?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BED_NUMBER_PATTERN = Pattern.compile("\\b(?:bed\\s*(?:no\\.?|number|#)?\\s*\\d+|ward\\s*(?:no\\.?|#)?\\s*\\d+|icu\\s*bed\\s*\\d*)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_LINK_PATTERN = Pattern.compile("(?:https?://|www\\.|t\\.me/|wa\\.me/|bit\\.ly/)[^\\s]+|\\b[a-zA-Z0-9-]+\\.(?:com|org|net|in|io|co|ai)(?:/[^\\s]*)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HIRING_PATTERN = Pattern.compile("\\b(?:we\\s+are\\s+hiring|hiring\\s+now|job\\s+vacancy|job\\s+opening|urgent\\s+requirement|walk[- ]?in\\s+interview|send\\s+(?:your\\s+)?(?:cv|resume)|contact\\s+for\\s+job|apply\\s+now|recruiting|open\\s+position)\\b", Pattern.CASE_INSENSITIVE);

    public void validateNoPhi(String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        if (BED_NUMBER_PATTERN.matcher(text).find()) {
            throw new BadRequestException("Clinical Anonymity violation: Bed or ward number detected. Please remove specific hospital bed and ward identifiers.");
        }

        if (PATIENT_IDENTIFIER_PATTERN.matcher(text).find()) {
            throw new BadRequestException("Patient Privacy violation: Patient name or personal identifier detected. Clinical answers and cases must be fully anonymized.");
        }

        if (PHONE_PATTERN.matcher(text).find()) {
            throw new BadRequestException("Protected Health Information (PHI) violation: Direct telephone or 10-digit number detected. Personal contacts are strictly prohibited.");
        }

        if (AADHAAR_PATTERN.matcher(text).find()) {
            throw new BadRequestException("Protected Health Information (PHI) violation: 12-digit Aadhaar / National ID detected.");
        }

        if (EMAIL_PATTERN.matcher(text).find()) {
            throw new BadRequestException("Protected Health Information (PHI) violation: Direct email address detected.");
        }

        if (HOSPITAL_ID_PATTERN.matcher(text).find()) {
            throw new BadRequestException("Protected Health Information (PHI) violation: Hospital Medical Record Number (MRN / UHID / OPD) detected.");
        }

        if (URL_LINK_PATTERN.matcher(text).find()) {
            throw new BadRequestException("Content Policy violation: External links and URLs are not permitted in clinical answers.");
        }

        if (HIRING_PATTERN.matcher(text).find()) {
            throw new BadRequestException("Content Policy violation: Job recruitment, hiring solicitations, and vacancies are restricted to the FALE Jobs portal.");
        }
    }
}
