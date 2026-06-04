package com.gme.remit.identity.api;

import com.gme.remit.identity.api.dto.OtpRequest;
import com.gme.remit.identity.api.dto.OtpResponse;
import com.gme.remit.identity.api.dto.PartyResponse;
import com.gme.remit.identity.api.dto.RegisterRequest;
import com.gme.remit.identity.domain.Otp;
import com.gme.remit.identity.domain.Party;
import com.gme.remit.identity.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Individual registration API (Feature 1.1.1). */
@RestController
@RequestMapping("/api/v1/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /** T-1.1.1-05 Request an OTP. T-1.1.1-06 validates the identifier format for the channel. */
    @PostMapping("/otp")
    public ResponseEntity<OtpResponse> requestOtp(@Valid @RequestBody OtpRequest req) {
        IdentifierValidator.validate(req.channel(), req.identifier());
        Otp otp = registrationService.requestOtp(req.identifier().trim(), req.channel());
        OtpResponse body = new OtpResponse("sent", otp.getExpiresAt(), otp.getCode());
        return ResponseEntity.accepted().body(body);
    }

    /** T-1.1.1-07 Submit registration. On a verified OTP an UNVERIFIED account is created (T-1.1.1-08). */
    @PostMapping
    public ResponseEntity<PartyResponse> register(@Valid @RequestBody RegisterRequest req) {
        IdentifierValidator.validate(req.channel(), req.identifier());
        Party party = registrationService.register(
                req.identifier().trim(), req.channel(), req.code(), req.fullName().trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(PartyResponse.from(party)); // T-1.1.1-09
    }
}
