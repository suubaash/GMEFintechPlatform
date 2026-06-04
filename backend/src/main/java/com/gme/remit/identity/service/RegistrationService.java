package com.gme.remit.identity.service;

import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;
import com.gme.remit.identity.domain.Otp;
import com.gme.remit.identity.domain.Party;
import com.gme.remit.identity.repo.PartyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates individual registration: request OTP, then create the account once the OTP verifies. */
@Service
public class RegistrationService {

    private final OtpService otpService;
    private final PartyRepository partyRepository;

    public RegistrationService(OtpService otpService, PartyRepository partyRepository) {
        this.otpService = otpService;
        this.partyRepository = partyRepository;
    }

    /** Issue an OTP to the given identifier (email or phone). */
    public Otp requestOtp(String identifier, String channel) {
        return otpService.issue(identifier, channel);
    }

    /**
     * T-1.1.1-08 Create an UNVERIFIED individual account, but only after the OTP verifies.
     * T-1.1.1-10 Reject a second account for the same identifier.
     */
    @Transactional
    public Party register(String identifier, String channel, String code, String fullName) {
        // Guard before consuming the OTP so a duplicate attempt does not burn a valid code.
        guardDuplicate(channel, identifier);

        otpService.verify(identifier, code, channel);

        Party party = "email".equals(channel)
                ? Party.newIndividual(identifier, null, fullName)
                : Party.newIndividual(null, identifier, fullName);

        try {
            return partyRepository.save(party);
        } catch (DataIntegrityViolationException e) {
            // Lost a race against the unique index (T-1.1.1-10 storage-layer guard).
            throw new DomainException(ErrorCode.DUPLICATE_IDENTIFIER,
                    "An account already exists for this identifier");
        }
    }

    private void guardDuplicate(String channel, String identifier) {
        boolean exists = "email".equals(channel)
                ? partyRepository.existsByEmail(identifier)
                : partyRepository.existsByPhone(identifier);
        if (exists) {
            throw new DomainException(ErrorCode.DUPLICATE_IDENTIFIER,
                    "An account already exists for this identifier");
        }
    }
}
