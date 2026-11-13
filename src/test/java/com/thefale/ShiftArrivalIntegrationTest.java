package com.thefale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.entity.*;
import com.thefale.repository.*;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.security.JwtTokenProvider;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES)
class ShiftArrivalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository candidates;
    @Autowired FacilityProfileRepository facilities;
    @Autowired HospitalBankAccountRepository banks;
    @Autowired FacilityWalletRepository wallets;
    @Autowired LocumShiftRepository shifts;
    @Autowired ShiftClaimRepository claims;
    @Autowired CandidateDocumentRepository documents;
    @Autowired CandidatePayoutMethodRepository payoutMethods;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired JwtTokenProvider jwt;
    @Autowired WalletTransactionRepository transactions;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired com.thefale.service.WalletDocumentNumberListener numberAllocator;
    FacilityProfile hospital; CandidateProfile nurse;
    String hospitalToken, nurseToken, adminToken;

    String token(String role) {
        User user = users.save(User.builder().email(UUID.randomUUID()+"@example.invalid").role(role)
                .status("ACTIVE").candidateLevel("PROFESSIONAL").isOnboarded(true).build());
        return jwt.generateAccessToken(CustomUserPrincipal.builder().id(user.getId()).role(role).enabled(true).build());
    }
    @BeforeEach void seed() {
        User facilityUser = users.save(User.builder().email(UUID.randomUUID()+"@example.invalid").role("ROLE_FACILITY").status("ACTIVE").isOnboarded(true).build());
        hospitalToken = jwt.generateAccessToken(CustomUserPrincipal.builder().id(facilityUser.getId()).role("ROLE_FACILITY").enabled(true).build());
        hospital = facilities.save(FacilityProfile.builder().userId(facilityUser.getId()).hospitalName("QA Hospital").facilityType("Hospital").city("Pune").state("Maharashtra").beds(100).licenseNumber("QA-LICENSE")
                .kycStatus("VERIFIED").contactPerson("QA Supervisor").contactPhone("+910000000001").contactEmail("qa@example.invalid").build());
        banks.save(HospitalBankAccount.builder().facilityId(hospital.getId()).bankName("QA Bank").accountNumberEncrypted("test-only")
                .accountNumberLast4("0001").ifsc("TEST0000001").accountHolderName("QA Hospital").status("Verified").verifiedAt(Instant.now()).build());
        wallets.save(FacilityWallet.builder().facilityId(hospital.getId()).availableBalance(new BigDecimal("50000")).build());
        User clinician = users.save(User.builder().email(UUID.randomUUID()+"@example.invalid").role("ROLE_CANDIDATE").candidateLevel("PROFESSIONAL").status("ACTIVE").isOnboarded(true).build());
        nurse = candidates.save(CandidateProfile.builder().userId(clinician.getId()).name("QA Nurse").profession("Nurse")
                .city("Pune").kycStatus("VERIFIED").experienceYears("3").skills("ICU").build());
        payoutMethods.save(CandidatePayoutMethod.builder().candidateId(nurse.getId()).methodType("bank").bankName("QA Bank").accountNumberEncrypted("123456780001").accountNumberLast4("0001").ifsc("TEST0000001").accountHolderName("QA Nurse").status("Verified").verifiedAt(Instant.now()).build());
        nurseToken = jwt.generateAccessToken(CustomUserPrincipal.builder().id(clinician.getId()).role("ROLE_CANDIDATE").candidateLevel("PROFESSIONAL").enabled(true).build());
        adminToken = token("ROLE_ADMIN");
    }
    Map<String,Object> details() {
        return Map.of("profession","Nurse","city","Pune","registrationRequired",true,"blsRequired",true,
                "minimumExperienceYears",2,"reportTo","ICU Supervisor","reportLocation","Block B, floor 2","dressRequirement","Own scrubs",
                "itemsToBring","Council ID card","reportingNotes","Use the staff entrance");
    }
    JsonNode postShift() throws Exception {
        Instant start = Instant.now().plusSeconds(86400);
        return postJson("/api/v1/shifts", hospitalToken, Map.of("title","ICU Nurse","department","Critical care","startAt",start.toString(),
                "endAt",start.plusSeconds(28800).toString(),"ratePerHourPaise",50000,"openings",1,"details",details()));
    }
    JsonNode postJson(String path, String token, Object body) throws Exception {
        String response = mvc.perform(post(path).header("Authorization","Bearer "+token).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data");
    }
    JsonNode completeDuty(UUID shiftId, boolean qr) throws Exception {
        // Move the fixture into its arrival window instead of waiting a day.
        LocumShift shift = shifts.findById(shiftId).orElseThrow();
        shift.setStartAt(Instant.now().minusSeconds(600)); shift.setEndAt(Instant.now().plusSeconds(28200)); shifts.saveAndFlush(shift);
        JsonNode application = postJson("/api/v1/shifts/"+shiftId+"/claim",nurseToken,Map.of());
        String id = application.path("id").asText();
        assertThat(application.path("status").asText()).isEqualTo("CLAIMED");
        assertThat(application.path("arrivalInstructions").isNull()).isTrue();
        assertThat(shifts.findById(shiftId).orElseThrow().getSlotsClaimed()).isZero();
        String approved = mvc.perform(patch("/api/v1/employer/applications/"+id+"/status").header("Authorization","Bearer "+hospitalToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode staff = json.readTree(approved).path("data");
        assertThat(staff.path("shiftId").asText()).isEqualTo(shiftId.toString());
        assertThat(Instant.parse(staff.path("shiftStartAt").asText())).isEqualTo(shifts.findById(shiftId).orElseThrow().getStartAt());
        assertThat(Instant.parse(staff.path("shiftEndAt").asText())).isEqualTo(shifts.findById(shiftId).orElseThrow().getEndAt());
        assertThat(staff.path("checkInPin").asText()).matches("[0-9]{6}");
        JsonNode mine = json.readTree(mvc.perform(get("/api/v1/shifts/claims").header("Authorization","Bearer "+nurseToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        for (JsonNode claim : mine) if (claim.path("id").asText().equals(id)) {
            assertThat(claim.path("checkInPin").isNull()).isTrue(); assertThat(claim.path("checkInQrToken").isNull()).isTrue();
            assertThat(claim.path("arrivalInstructions").path("reportTo").asText()).isEqualTo("ICU Supervisor");
        }
        Map<String,Object> evidence = new HashMap<>(); evidence.put("method",qr?"QR":"PIN");
        evidence.put(qr?"qrToken":"pin",staff.path(qr?"checkInQrToken":"checkInPin").asText());
        evidence.put("latitude",18.52); evidence.put("longitude",73.85); evidence.put("accuracy",10);
        evidence.put("locationCapturedAt",Instant.now().toString()); evidence.put("selfieCapturedAt",Instant.now().toString());
        evidence.put("selfieDataUrl",selfie());
        Map<String,Object> incorrect = new HashMap<>(evidence);
        incorrect.put("method","PIN"); incorrect.put("pin","000000"); incorrect.remove("qrToken");
        mvc.perform(post("/api/v1/shifts/claims/"+id+"/check-in").header("Authorization","Bearer "+nurseToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(incorrect))).andExpect(status().isBadRequest());
        assertThat(claims.findById(UUID.fromString(id)).orElseThrow().getCheckInFailedAttempts()).isEqualTo(1);
        assertThat(postJson("/api/v1/shifts/claims/"+id+"/check-in",nurseToken,evidence).path("status").asText()).isEqualTo("CHECKED_IN");
        mvc.perform(get("/api/v1/shifts/claims/"+id+"/attendance").header("Authorization","Bearer "+hospitalToken))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.latitude").value(18.52));
        mvc.perform(get("/api/v1/shifts/claims/"+id+"/attendance").header("Authorization","Bearer "+token("ROLE_CANDIDATE")))
                .andExpect(status().isForbidden());
        assertThat(postJson("/api/v1/shifts/claims/"+id+"/complete",nurseToken,Map.of("actualHours",8)).path("status").asText()).isEqualTo("TIMESHEET_SUBMITTED");
        JsonNode completed = postJson("/api/v1/shifts/claims/"+id+"/complete",hospitalToken,Map.of("actualHours",8));
        assertThat(completed.path("platformFeePaise").asLong()).isZero();
        assertThat(completed.path("payoutStatus").asText()).isEqualTo("PENDING_DISBURSEMENT");
        return completed;
    }
    @Test void approvedPostingApplicationAttendanceAndHospitalFeeLifecycle() throws Exception {
        JsonNode first = postShift(); UUID firstId = UUID.fromString(first.path("id").asText());
        assertThat(first.path("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(first.path("facilityFeePercent").asDouble()).isEqualTo(8);
        assertThat(first.path("platformFeeGstPercent").asDouble()).isEqualTo(18);
        assertThat(first.path("platformFeeGstPaise").asLong()).isEqualTo(5760);
        assertThat(first.path("totalEscrowRequiredPaise").asLong()).isEqualTo(437760);
        String publicBefore = mvc.perform(get("/api/v1/shifts")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(publicBefore).doesNotContain(firstId.toString());
        postJson("/api/v1/admin/shifts/"+firstId+"/approve",adminToken,Map.of("action","APPROVE"));
        String publicAfter = mvc.perform(get("/api/v1/shifts")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(publicAfter).contains(firstId.toString()).doesNotContain("ICU Supervisor","staff entrance");
        mvc.perform(post("/api/v1/shifts/"+firstId+"/claim").header("Authorization","Bearer "+nurseToken)).andExpect(status().isBadRequest());
        documents.save(CandidateDocument.builder().candidateId(nurse.getId()).documentType("BLS").originalFileName("BLS.pdf")
                .fileUrl("test-only").fileSizeBytes(100L).mimeType("application/pdf").verified(true).build());
        JsonNode completedFirst = completeDuty(firstId,false);
        assertThat(claims.findById(UUID.fromString(completedFirst.path("id").asText())).orElseThrow().getHospitalFeeAmount()).isEqualByComparingTo("0");
        JsonNode second = postShift(); assertThat(second.path("status").asText()).isEqualTo("OPEN");
        JsonNode completedSecond = completeDuty(UUID.fromString(second.path("id").asText()),true);
        assertThat(claims.findById(UUID.fromString(completedSecond.path("id").asText())).orElseThrow().getHospitalFeeAmount()).isEqualByComparingTo("320");
        FacilityWallet wallet = wallets.findByFacilityId(hospital.getId()).orElseThrow();
        assertThat(claims.findById(UUID.fromString(completedFirst.path("id").asText())).orElseThrow().getHospitalFeeGstAmount()).isEqualByComparingTo("0");
        assertThat(claims.findById(UUID.fromString(completedSecond.path("id").asText())).orElseThrow().getHospitalFeeGstAmount()).isEqualByComparingTo("57.60");
        assertThat(wallet.getFirstCompletedShiftId()).isEqualTo(firstId);
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("41622.40");
        assertThat(wallet.getLockedEscrowBalance()).isEqualByComparingTo("8377.60");
        String queuedInvoice = transactions.findByClaimIdAndType(UUID.fromString(completedSecond.path("id").asText()),"disbursement").orElseThrow().getDocumentNumber();
        assertThat(queuedInvoice).matches("INV-[0-9]{4}-[0-9]{6,}");
        postJson("/api/v1/admin/payouts/"+completedSecond.path("id").asText()+"/disburse",adminToken,Map.of("utr","GST"+System.nanoTime()));
        UUID paidClaim=UUID.fromString(completedSecond.path("id").asText());
        var settlement=transactions.findByClaimIdAndType(paidClaim,"disbursement").orElseThrow();
        assertThat(transactions.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream().filter(t -> paidClaim.equals(t.getClaimId())).count()).isEqualTo(1);
        assertThat(settlement.getStatus()).isEqualTo("Completed");
        assertThat(settlement.getDocumentNumber()).isEqualTo(queuedInvoice);
        assertThat(settlement.getInvoiceId()).isEqualTo(queuedInvoice);
        assertThat(settlement.getDestinationSnapshot()).isEqualTo("QA Bank ****0001");
        assertThat(settlement.getBankDisbursedAt()).isNotNull();
        var changed=payoutMethods.findByCandidateId(nurse.getId()).orElseThrow(); changed.setBankName("Changed Bank"); changed.setAccountNumberLast4("9999"); changed.setStatus("Pending"); payoutMethods.saveAndFlush(changed);
        assertThat(claims.findById(paidClaim).orElseThrow().getPayoutMethodSnapshot()).isEqualTo("QA Bank ****0001");
        FacilityWallet paid = wallets.findByFacilityId(hospital.getId()).orElseThrow();
        assertThat(paid.getLockedEscrowBalance()).isEqualByComparingTo("4000");
        assertThat(paid.getTotalDisbursed()).isEqualByComparingTo("4377.60");
        String ledger = mvc.perform(get("/api/v1/employer/wallet/transactions").header("Authorization","Bearer "+hospitalToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ledger).contains("platformFeeGstPaise").contains("5760");

    }

    @Test void rejectedAndCancelledReservationsRefundFeeAndGst() throws Exception {
        JsonNode pending = postShift();
        postJson("/api/v1/admin/shifts/"+pending.path("id").asText()+"/review",adminToken,Map.of("action","REJECT","notes","QA rejection"));
        FacilityWallet wallet = wallets.findByFacilityId(hospital.getId()).orElseThrow();
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("50000");
        assertThat(wallet.getLockedEscrowBalance()).isEqualByComparingTo("0");
        JsonNode another = postShift();
        mvc.perform(delete("/api/v1/shifts/"+another.path("id").asText()).header("Authorization","Bearer "+hospitalToken)).andExpect(status().isOk());
        wallet = wallets.findByFacilityId(hospital.getId()).orElseThrow();
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("50000");
        assertThat(wallet.getLockedEscrowBalance()).isEqualByComparingTo("0");
        assertThat(transactions.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream().filter(t -> "refund".equals(t.getType()))
                .map(WalletTransaction::getPlatformFeeGstAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("115.20");
    }

    @Test void editedProfessionIsPersistedAndUsedForSubsequentApplications() throws Exception {
        JsonNode posted = postShift();
        UUID id = UUID.fromString(posted.path("id").asText());
        postJson("/api/v1/admin/shifts/"+id+"/approve",adminToken,Map.of("action","APPROVE"));
        Map<String,Object> updatedDetails = new HashMap<>(details());
        updatedDetails.put("profession","Doctor"); updatedDetails.put("blsRequired",false);
        updatedDetails.put("reportTo","Jino"); updatedDetails.put("reportLocation","ICU");
        Map<String,Object> request = Map.of("title","ICU Doctor","department","Critical care",
                "startAt",posted.path("startAt").asText(),"endAt",posted.path("endAt").asText(),
                "ratePerHourPaise",50000,"openings",1,"details",updatedDetails);
        mvc.perform(put("/api/v1/shifts/"+id).header("Authorization","Bearer "+hospitalToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.details.profession").value("Doctor"))
                .andExpect(jsonPath("$.data.details.reportTo").value("Jino"))
                .andExpect(jsonPath("$.data.details.reportLocation").value("ICU"));
        assertThat(shifts.findById(id).orElseThrow().getDetails().getProfession()).isEqualTo("Doctor");
        assertThat(shifts.findById(id).orElseThrow().getDetails().getReportTo()).isEqualTo("Jino");
        mvc.perform(post("/api/v1/shifts/"+id+"/claim").header("Authorization","Bearer "+nurseToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("This shift requires profession: Doctor"));
        nurse.setProfession("Doctor"); candidates.saveAndFlush(nurse);
        assertThat(postJson("/api/v1/shifts/"+id+"/claim",nurseToken,Map.of()).path("status").asText()).isEqualTo("CLAIMED");
        String publicList = mvc.perform(get("/api/v1/shifts")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(publicList).contains("Doctor").doesNotContain("Jino","staff entrance");
    }

    @Test void simultaneousCompletionsGrantOnlyOneHospitalFreeShift() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int day = 1; day <= 2; day++) {
            Instant start = Instant.now().minusSeconds(day * 86400L);
            LocumShift shift = shifts.save(LocumShift.builder().facilityId(hospital.getId()).title("QA Nurse")
                    .department("Critical care").startAt(start).endAt(start.plusSeconds(28800)).hours(8)
                    .ratePerHour(new BigDecimal("500")).openings(1).slotsClaimed(1).status("FILLED")
                    .facilityFeePercent(new BigDecimal("8")).facilityFeeAmount(new BigDecimal("320"))
                    .platformFeeGstPercent(new BigDecimal("18")).platformFeeGstAmount(new BigDecimal("57.60")).build());
            ShiftClaim claim = claims.save(ShiftClaim.builder().shiftId(shift.getId()).candidateId(nurse.getId())
                    .status("TIMESHEET_SUBMITTED").actualHours(8).grossAmount(new BigDecimal("4000"))
                    .platformFee(BigDecimal.ZERO).tdsAmount(new BigDecimal("40")).netAmount(new BigDecimal("3960")).build());
            ids.add(claim.getId());
        }
        FacilityWallet funded = wallets.findByFacilityId(hospital.getId()).orElseThrow();
        funded.setAvailableBalance(new BigDecimal("41244.80")); funded.setLockedEscrowBalance(new BigDecimal("8755.20")); wallets.saveAndFlush(funded);
        List<CompletableFuture<Void>> requests = ids.stream().map(id -> CompletableFuture.runAsync(() -> {
            try { postJson("/api/v1/shifts/claims/"+id+"/complete",hospitalToken,Map.of("actualHours",8)); }
            catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        })).toList();
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).get(20,java.util.concurrent.TimeUnit.SECONDS);
        List<BigDecimal> fees = ids.stream().map(id -> claims.findById(id).orElseThrow().getHospitalFeeAmount()).sorted().toList();
        assertThat(fees.get(0)).isEqualByComparingTo("0"); assertThat(fees.get(1)).isEqualByComparingTo("320");
        FacilityWallet wallet = wallets.findByFacilityId(hospital.getId()).orElseThrow();
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("41622.40");
        assertThat(wallet.getLockedEscrowBalance()).isEqualByComparingTo("8377.60");
    }
    private String selfie() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(160,160,java.awt.image.BufferedImage.TYPE_INT_RGB),"jpeg",bytes);
        return "data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    @Test void depositsRequireAdminReconciliationAndCannotCreditTwice() throws Exception {
        String utr="QA"+System.nanoTime();
        var before=wallets.findByFacilityId(hospital.getId()).orElseThrow().getAvailableBalance();
        postJson("/api/v1/employer/wallet/topup",hospitalToken,Map.of("amountPaise",123456,"paymentMethod","neft","utr",utr));
        assertThat(wallets.findByFacilityId(hospital.getId()).orElseThrow().getAvailableBalance()).isEqualByComparingTo(before);
        var tx=transactions.findByWalletIdOrderByCreatedAtDesc(wallets.findByFacilityId(hospital.getId()).orElseThrow().getId()).getFirst();
        assertThat(tx.getStatus()).isEqualTo("Pending Reconciliation");
        assertThat(tx.getInvoiceId()).matches("INV-[0-9]{4}-[0-9]{6,}").isEqualTo(tx.getDocumentNumber());
        String path="/api/v1/admin/finance/deposits/"+tx.getId()+"/review";
        var body=Map.of("approved",true,"bankReference",utr,"notes","Bank statement received and reconciled.");
        mvc.perform(post(path).header("Authorization","Bearer "+hospitalToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isForbidden());
        postJson(path,adminToken,body);
        assertThat(wallets.findByFacilityId(hospital.getId()).orElseThrow().getAvailableBalance()).isEqualByComparingTo(before.add(new BigDecimal("1234.56")));
        mvc.perform(post(path).header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        assertThat(wallets.findByFacilityId(hospital.getId()).orElseThrow().getAvailableBalance()).isEqualByComparingTo(before.add(new BigDecimal("1234.56")));
    }
    @Test void creditQuantityAndInsufficientFundsCannotMintCredits() throws Exception {
        var wallet=wallets.findByFacilityId(hospital.getId()).orElseThrow(); wallet.setAvailableBalance(BigDecimal.ZERO); wallets.saveAndFlush(wallet);
        for (var body: List.of(Map.of("packageId","pkg-1","credits",999),Map.of("packageId","pkg-1","credits",10))) {
            mvc.perform(post("/api/v1/employer/wallet/buy-credits").header("Authorization","Bearer "+hospitalToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        }
        assertThat(wallets.findByFacilityId(hospital.getId()).orElseThrow().getCandidateCredits()).isZero();
    }
    @Test void bankAccountsAreEncryptedAtRest() {
        var encrypted=jdbc.queryForObject("select account_number_encrypted from candidate_payout_methods where candidate_id=?",String.class,nurse.getId());
        assertThat(encrypted).startsWith("AESGCM1:").doesNotContain("123456780001");
        assertThat(payoutMethods.findByCandidateId(nurse.getId()).orElseThrow().getAccountNumberEncrypted()).isEqualTo("123456780001");
    }

    @Test void separateDepositsReconcileConcurrentlyWithoutLosingFunds() throws Exception {
        String a="QA"+System.nanoTime(),b="QB"+System.nanoTime();
        var before=wallets.findByFacilityId(hospital.getId()).orElseThrow().getAvailableBalance();
        postJson("/api/v1/employer/wallet/topup",hospitalToken,Map.of("amountPaise",100001,"paymentMethod","neft","utr",a));
        postJson("/api/v1/employer/wallet/topup",hospitalToken,Map.of("amountPaise",200002,"paymentMethod","neft","utr",b));
        var rows=transactions.findByWalletIdOrderByCreatedAtDesc(wallets.findByFacilityId(hospital.getId()).orElseThrow().getId());
        var calls=rows.stream().filter(t -> "topup".equals(t.getType())).map(t -> CompletableFuture.runAsync(()->{
            try { postJson("/api/v1/admin/finance/deposits/"+t.getId()+"/review",adminToken,Map.of("approved",true,"bankReference",t.getUtr(),"notes","Bank receipt independently checked.")); }
            catch(Exception error) { throw new RuntimeException(error); }
        })).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(calls).join();
        assertThat(wallets.findByFacilityId(hospital.getId()).orElseThrow().getAvailableBalance()).isEqualByComparingTo(before.add(new BigDecimal("3000.03")));
    }

    @Test void concurrentDocumentAllocationsAreUnique() {
        var allocations=java.util.stream.IntStream.range(0,20).mapToObj(index->CompletableFuture.supplyAsync(()->{
            var transaction=new WalletTransaction(); numberAllocator.assignNumber(transaction);
            assertThat(transaction.getInvoiceId()).isEqualTo(transaction.getDocumentNumber());
            return transaction.getDocumentNumber();
        })).toList();
        var numbers=allocations.stream().map(CompletableFuture::join).toList();
        assertThat(new HashSet<>(numbers)).hasSize(20);
        numbers.forEach(number->assertThat(number).matches("INV-[0-9]{4}-[0-9]{6,}"));
    }
    @Test void numberingMigrationRetainsOldReferencesAndLegacyDocumentClassification() throws Exception {
        try(var connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try(var sql=connection.createStatement()) {
                String schema="qa_invoice_"+UUID.randomUUID().toString().replace("-","");
                sql.execute("CREATE SCHEMA "+schema);sql.execute("SET LOCAL search_path TO "+schema);
                sql.execute("CREATE TABLE wallet_transactions(id UUID PRIMARY KEY,created_at TIMESTAMPTZ NOT NULL,invoice_id VARCHAR(50),document_number VARCHAR(64))");
                sql.execute("INSERT INTO wallet_transactions VALUES('11111111-1111-4111-8111-111111111111','2026-12-31T18:29:00Z','INV-2026-F6105A',NULL),('22222222-2222-4222-8222-222222222222','2026-12-31T18:31:00Z','SET-old-reference','SET-old-reference')");
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("db/migration/V24__sequential_wallet_document_numbers.sql"));
                try(var rows=sql.executeQuery("SELECT * FROM wallet_transactions ORDER BY created_at")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString("invoice_id")).isEqualTo("INV-2026-000001");
                    assertThat(rows.getString("previous_invoice_id")).isEqualTo("INV-2026-F6105A"); assertThat(rows.getString("document_number")).isNull();
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString("invoice_id")).isEqualTo("INV-2027-000002");
                    assertThat(rows.getString("document_number")).isEqualTo("INV-2027-000002"); assertThat(rows.getString("previous_document_number")).isEqualTo("SET-old-reference");
                }
                try(var next=sql.executeQuery("SELECT nextval('wallet_document_number_seq')")) {assertThat(next.next()).isTrue();assertThat(next.getLong(1)).isEqualTo(3);}
            } finally {connection.rollback();connection.setAutoCommit(true);}
        }
    }
}



