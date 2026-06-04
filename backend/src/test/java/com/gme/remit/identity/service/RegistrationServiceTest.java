package com.gme.remit.identity.service;

import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;
import com.gme.remit.identity.domain.AccountStatus;
import com.gme.remit.identity.domain.Party;
import com.gme.remit.identity.repo.PartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationServiceTest {

    private OtpService otpService;
    private PartyRepository partyRepository;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        otpService = mock(OtpService.class);
        partyRepository = mock(PartyRepository.class);
        service = new RegistrationService(otpService, partyRepository);
    }

    @Test // T-1.1.1-21 happy path: verified OTP -> UNVERIFIED account created
    void createsUnverifiedAccountOnVerifiedOtp() {
        when(partyRepository.existsByEmail("you@example.com")).thenReturn(false);
        when(partyRepository.save(any(Party.class))).thenAnswer(inv -> inv.getArgument(0));

        Party party = service.register("you@example.com", "email", "123456", "Jane Doe");

        assertThat(party.getStatus()).isEqualTo(AccountStatus.UNVERIFIED);
        assertThat(party.getEmail()).isEqualTo("you@example.com");
        verify(otpService).verify("you@example.com", "123456", "email");
        verify(partyRepository).save(any(Party.class));
    }

    @Test // T-1.1.1-23 duplicate identifier rejected, OTP never burned
    void rejectsDuplicateIdentifier() {
        when(partyRepository.existsByEmail("you@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("you@example.com", "email", "123456", "Jane Doe"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.DUPLICATE_IDENTIFIER);

        verify(otpService, never()).verify(anyString(), anyString(), anyString());
        verify(partyRepository, never()).save(any());
    }

    @Test // T-1.1.1-22 invalid OTP -> no account created
    void noAccountWhenOtpInvalid() {
        when(partyRepository.existsByEmail("you@example.com")).thenReturn(false);
        doThrow(new DomainException(ErrorCode.OTP_INVALID, "bad"))
                .when(otpService).verify("you@example.com", "000000", "email");

        assertThatThrownBy(() -> service.register("you@example.com", "email", "000000", "Jane Doe"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.OTP_INVALID);

        verify(partyRepository, never()).save(any());
    }
}
