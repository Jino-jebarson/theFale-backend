package com.thefale.service;

import com.thefale.dto.CandidateReferralDashboardDto;
import com.thefale.dto.CandidateReferralEntryDto;
import com.thefale.entity.CandidateProfile;
import com.thefale.entity.CandidateReferral;
import com.thefale.entity.CandidateReferralCode;
import com.thefale.entity.Notification;
import com.thefale.entity.User;
import com.thefale.exception.BadRequestException;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.CandidatePayoutMethodRepository;
import com.thefale.repository.CandidateProfileRepository;
import com.thefale.repository.CandidateReferralCodeRepository;
import com.thefale.repository.CandidateReferralRepository;
import com.thefale.repository.NotificationRepository;
import com.thefale.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class ReferralService {
    private static final int FIRST_SHIFT_REWARD_RUPEES = 200;
    private static final int HIRED_REWARD_RUPEES = 500;
    private static final int MONTHLY_REWARD_LIMIT = 5;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateReferralCodeRepository codeRepository;
    private final CandidateReferralRepository referralRepository;
    private final UserRepository userRepository;
    private final CandidatePayoutMethodRepository payoutMethodRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public CandidateReferralDashboardDto getDashboard(UUID userId) {
        CandidateProfile profile = candidateProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found. Complete onboarding first."));
        String code = ensureCode(profile.getId()).getReferralCode();
        List<CandidateReferral> referrals = referralRepository.findByReferrerCandidateIdOrderByCreatedAtDesc(profile.getId());
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal due = BigDecimal.ZERO;
        List<CandidateReferralEntryDto> entries = new ArrayList<>();
        for (CandidateReferral referral : referrals) {
            CandidateProfile referred = candidateProfileRepository.findById(referral.getReferredCandidateId()).orElse(null);
            if (referred == null) continue;
            String status = "VERIFIED".equalsIgnoreCase(referred.getKycStatus()) && "JOINED".equals(referral.getStatus())
                    ? "VERIFIED" : referral.getStatus();
            BigDecimal amount = referral.getRewardAmount() == null ? BigDecimal.ZERO : referral.getRewardAmount();
            if ("DUE".equals(referral.getRewardStatus())) { earned = earned.add(amount); due = due.add(amount); }
            if ("PAID".equals(referral.getRewardStatus())) { earned = earned.add(amount); paid = paid.add(amount); }
            entries.add(CandidateReferralEntryDto.builder()
                    .id(referral.getId())
                    .candidateName(referred.getName())
                    .status(status)
                    .rewardType(referral.getRewardType())
                    .rewardAmount(referral.getRewardAmount())
                    .rewardStatus(referral.getRewardStatus())
                    .rewardReason(referral.getRewardReason())
                    .joinedAt(referral.getCreatedAt())
                    .qualifiedAt(referral.getQualifiedAt())
                    .paidAt(referral.getPaidAt())
                    .build());
        }
        return CandidateReferralDashboardDto.builder()
                .referralCode(code)
                .firstShiftRewardRupees(FIRST_SHIFT_REWARD_RUPEES)
                .hireRewardRupees(HIRED_REWARD_RUPEES)
                .monthlyRewardLimit(MONTHLY_REWARD_LIMIT)
                .invitedCount(referrals.size())
                .earnedAmount(earned)
                .paidAmount(paid)
                .dueAmount(due)
                .referrals(entries)
                .build();
    }

    @Transactional
    public CandidateReferral applyCode(UUID referredUserId, UUID referredCandidateId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        CandidateReferralCode savedCode = codeRepository.findByReferralCodeIgnoreCase(code)
                .orElseThrow(() -> new BadRequestException("Referral code was not found."));
        CandidateProfile referrer = candidateProfileRepository.findById(savedCode.getCandidateId())
                .orElseThrow(() -> new BadRequestException("Referral code is no longer active."));
        CandidateProfile referred = candidateProfileRepository.findById(referredCandidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found."));
        if (referrer.getId().equals(referredCandidateId) || referrer.getUserId().equals(referredUserId)) {
            throw new BadRequestException("You cannot use your own referral code.");
        }
        var existingReferral = referralRepository.findByReferredCandidateId(referredCandidateId);
        if (existingReferral.isPresent()) {
            if (existingReferral.get().getReferralCode().equalsIgnoreCase(code)) return existingReferral.get();
            throw new BadRequestException("A referral code has already been applied to this account.");
        }
        User referrerUser = userRepository.findById(referrer.getUserId()).orElse(null);
        User referredUser = userRepository.findById(referredUserId).orElse(null);
        if (referrerUser != null && referredUser != null && normalizePhone(referrerUser.getPhone()) != null
                && normalizePhone(referrerUser.getPhone()).equals(normalizePhone(referredUser.getPhone()))) {
            throw new BadRequestException("This referral cannot be used for the same phone number.");
        }
        CandidateReferral referral = CandidateReferral.builder()
                .referrerCandidateId(referrer.getId())
                .referredCandidateId(referred.getId())
                .referralCode(code)
                .status("JOINED")
                .rewardStatus("NONE")
                .build();
        try {
            CandidateReferral result = referralRepository.saveAndFlush(referral);
            notificationRepository.save(Notification.builder()
                    .userId(referrer.getUserId())
                    .title("A friend joined The Fale")
                    .message(referred.getName() + " joined with your referral code. We will update you when they are verified and start work.")
                    .type("REFERRAL")
                    .actionUrl("/profile")
                    .read(false)
                    .build());
            return result;
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("A referral code has already been applied to this account.");
        }
    }

    @Transactional
    public void qualifyForHire(UUID referredCandidateId) {
        qualify(referredCandidateId, "HIRED", HIRED_REWARD_RUPEES);
    }

    @Transactional
    public void qualifyForFirstCompletedShift(UUID referredCandidateId) {
        qualify(referredCandidateId, "FIRST_SHIFT", FIRST_SHIFT_REWARD_RUPEES);
    }

    private void qualify(UUID referredCandidateId, String rewardType, int rewardRupees) {
        CandidateReferral referral = referralRepository.findByReferredCandidateIdForUpdate(referredCandidateId).orElse(null);
        if (referral == null || !"NONE".equals(referral.getRewardStatus())) return;
        CandidateProfile referrer = candidateProfileRepository.findByIdWithLock(referral.getReferrerCandidateId()).orElse(null);
        CandidateProfile referred = candidateProfileRepository.findById(referredCandidateId).orElse(null);
        if (referrer == null || referred == null) return;

        Instant now = Instant.now();
        Instant monthStart = YearMonth.from(now.atZone(ZoneOffset.UTC)).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long awardedThisMonth = referralRepository.countByReferrerCandidateIdAndQualifiedAtGreaterThanEqualAndRewardStatusIn(
                referrer.getId(), monthStart, List.of("DUE", "PAID"));
        String holdReason = duplicatePayoutDestination(referrer.getId(), referredCandidateId)
                ? "Matching phone or payout destination; requires admin review."
                : awardedThisMonth >= MONTHLY_REWARD_LIMIT ? "Monthly referral reward limit reached; requires admin review." : null;

        referral.setStatus("HIRED".equals(rewardType) ? "HIRED" : "WORKED");
        referral.setRewardType(rewardType);
        referral.setRewardAmount(BigDecimal.valueOf(rewardRupees));
        referral.setQualifiedAt(now);
        referral.setRewardReason(holdReason);
        referral.setRewardStatus(holdReason == null ? "DUE" : "HELD");
        referralRepository.save(referral);

        notificationRepository.save(Notification.builder()
                .userId(referrer.getUserId())
                .title(holdReason == null ? "Referral reward earned" : "Referral reward needs review")
                .message(holdReason == null
                        ? "You earned ₹" + rewardRupees + " from a referral. The reward is ready for payout."
                        : "A referral reward is on hold for review: " + holdReason)
                .type("REFERRAL")
                .actionUrl("/profile")
                .read(false)
                .build());
    }

    @Transactional(readOnly = true)
    public List<CandidateReferralEntryDto> getAdminRewards() {
        List<CandidateReferralEntryDto> result = new ArrayList<>();
        for (String status : List.of("DUE", "HELD")) {
            for (CandidateReferral referral : referralRepository.findByRewardStatusOrderByQualifiedAtAsc(status)) {
                CandidateProfile referrer = candidateProfileRepository.findById(referral.getReferrerCandidateId()).orElse(null);
                CandidateProfile referred = candidateProfileRepository.findById(referral.getReferredCandidateId()).orElse(null);
                result.add(CandidateReferralEntryDto.builder()
                        .id(referral.getId())
                        .candidateName((referrer == null ? "Unknown referrer" : referrer.getName()) + " → " + (referred == null ? "Unknown candidate" : referred.getName()))
                        .status(referral.getStatus())
                        .rewardType(referral.getRewardType())
                        .rewardAmount(referral.getRewardAmount())
                        .rewardStatus(referral.getRewardStatus())
                        .rewardReason(referral.getRewardReason())
                        .joinedAt(referral.getCreatedAt())
                        .qualifiedAt(referral.getQualifiedAt())
                        .paidAt(referral.getPaidAt())
                        .build());
            }
        }
        return result;
    }

    @Transactional
    public CandidateReferralEntryDto releaseHeldReward(UUID referralId, UUID adminUserId) {
        CandidateReferral referral = referralRepository.findById(referralId)
                .orElseThrow(() -> new ResourceNotFoundException("Referral reward not found."));
        if (!"HELD".equals(referral.getRewardStatus())) throw new BadRequestException("Only held rewards can be released for payout.");
        referral.setRewardStatus("DUE");
        referral.setRewardReason(null);
        CandidateReferral saved = referralRepository.save(referral);
        auditLogService.logEvent(adminUserId, "REFERRAL_REWARD_RELEASED", "CandidateReferral", saved.getId().toString(), null);
        return mapAdminEntry(saved);
    }

    @Transactional
    public CandidateReferralEntryDto markRewardPaid(UUID referralId, String paymentReference, UUID adminUserId) {
        CandidateReferral referral = referralRepository.findById(referralId)
                .orElseThrow(() -> new ResourceNotFoundException("Referral reward not found."));
        if (!"DUE".equals(referral.getRewardStatus())) throw new BadRequestException("Only rewards ready for payout can be marked paid.");
        String reference = paymentReference == null ? "" : paymentReference.trim().toUpperCase(Locale.ROOT);
        if (!reference.matches("[A-Z0-9-]{6,50}")) throw new BadRequestException("Enter the actual 6-50 character bank or UPI payment reference.");
        referral.setRewardStatus("PAID");
        referral.setPaymentReference(reference);
        referral.setPaidAt(Instant.now());
        CandidateReferral saved = referralRepository.save(referral);
        auditLogService.logEvent(adminUserId, "REFERRAL_REWARD_PAID", "CandidateReferral", saved.getId().toString(), java.util.Map.of("paymentReference", reference));
        CandidateProfile referrer = candidateProfileRepository.findById(saved.getReferrerCandidateId()).orElse(null);
        if (referrer != null) notificationRepository.save(Notification.builder()
                .userId(referrer.getUserId())
                .title("Referral reward paid")
                .message("Your referral reward of ₹" + saved.getRewardAmount() + " has been paid. Reference: " + reference)
                .type("REFERRAL")
                .actionUrl("/profile")
                .read(false)
                .build());
        return mapAdminEntry(saved);
    }

    private CandidateReferralEntryDto mapAdminEntry(CandidateReferral referral) {
        CandidateProfile referrer = candidateProfileRepository.findById(referral.getReferrerCandidateId()).orElse(null);
        CandidateProfile referred = candidateProfileRepository.findById(referral.getReferredCandidateId()).orElse(null);
        return CandidateReferralEntryDto.builder()
                .id(referral.getId())
                .candidateName((referrer == null ? "Unknown referrer" : referrer.getName()) + " → " + (referred == null ? "Unknown candidate" : referred.getName()))
                .status(referral.getStatus()).rewardType(referral.getRewardType()).rewardAmount(referral.getRewardAmount())
                .rewardStatus(referral.getRewardStatus()).rewardReason(referral.getRewardReason())
                .joinedAt(referral.getCreatedAt()).qualifiedAt(referral.getQualifiedAt()).paidAt(referral.getPaidAt()).build();
    }

    private CandidateReferralCode ensureCode(UUID candidateId) {
        return codeRepository.findById(candidateId).orElseGet(() -> {
            for (int attempt = 0; attempt < 8; attempt++) {
                CandidateReferralCode code = CandidateReferralCode.builder()
                        .candidateId(candidateId)
                        .referralCode(generateCode())
                        .createdAt(Instant.now())
                        .build();
                try {
                    return codeRepository.saveAndFlush(code);
                } catch (DataIntegrityViolationException ex) {
                    CandidateReferralCode existing = codeRepository.findById(candidateId).orElse(null);
                    if (existing != null) return existing;
                }
            }
            throw new IllegalStateException("Could not reserve a unique referral code.");
        });
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder("FL");
        for (int i = 0; i < 6; i++) code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return code.toString();
    }

    private boolean duplicatePayoutDestination(UUID referrerId, UUID referredId) {
        User referrer = candidateProfileRepository.findById(referrerId).flatMap(p -> userRepository.findById(p.getUserId())).orElse(null);
        User referred = candidateProfileRepository.findById(referredId).flatMap(p -> userRepository.findById(p.getUserId())).orElse(null);
        if (referrer != null && referred != null && normalizePhone(referrer.getPhone()) != null
                && normalizePhone(referrer.getPhone()).equals(normalizePhone(referred.getPhone()))) return true;
        var a = payoutMethodRepository.findByCandidateId(referrerId).orElse(null);
        var b = payoutMethodRepository.findByCandidateId(referredId).orElse(null);
        if (a == null || b == null) return false;
        String aUpi = normalize(a.getUpiId());
        String bUpi = normalize(b.getUpiId());
        if (aUpi != null && aUpi.equals(bUpi)) return true;
        String aAccount = normalize(a.getAccountNumberEncrypted());
        String bAccount = normalize(b.getAccountNumberEncrypted());
        return aAccount != null && aAccount.equals(bAccount);
    }

    private String normalizePhone(String value) { return normalize(value) == null ? null : normalize(value).replaceAll("\\D", ""); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
}
