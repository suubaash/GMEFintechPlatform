package com.gme.remit.identity.repo;

import com.gme.remit.identity.domain.Otp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface OtpRepository extends JpaRepository<Otp, UUID> {

    /** Latest still-usable OTP for an identifier: not consumed, not expired. */
    Optional<Otp> findFirstByIdentifierAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String identifier, OffsetDateTime now);

    long countByIdentifierAndCreatedAtAfter(String identifier, OffsetDateTime since);

    Optional<Otp> findFirstByIdentifierOrderByCreatedAtDesc(String identifier);
}
