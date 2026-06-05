package com.gme.remit.transfer.api.dto;

import com.gme.remit.common.money.Money;
import com.gme.remit.ledger.domain.JournalVoucher;
import com.gme.remit.ledger.domain.Posting;
import com.gme.remit.transfer.domain.Leg;
import com.gme.remit.transfer.domain.SwiftMessage;
import com.gme.remit.transfer.domain.Transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Full transfer view: status, legs, generated SWIFT, and the double-entry journal. */
public record TransferResponse(
        UUID transferId,
        UUID quoteId,
        String corridor,
        String status,
        String sendCurrency,
        String sendAmount,
        String receiveCurrency,
        String receiveAmount,
        BigDecimal quotedRate,
        String totalFees,
        String senderName,
        String recipientName,
        String recipientAccount,
        String recipientBankBic,
        OffsetDateTime createdAt,
        List<LegDto> legs,
        List<SwiftDto> swift,
        List<JournalDto> journal
) {
    public record LegDto(String kind, String status, String currency, long amountMinor, String amount) {
    }

    public record SwiftDto(String messageType, String uetr, String reference, String finText) {
    }

    public record PostingDto(String accountCode, String direction, long amountMinor) {
    }

    public record JournalDto(String movementType, String currency, long amountMinor,
                             List<PostingDto> postings) {
    }

    public static TransferResponse from(Transfer t, List<Leg> legs, List<SwiftMessage> swift,
                                        List<JournalVoucher> jvs, List<Posting> postings) {
        List<LegDto> legDtos = legs.stream()
                .map(l -> new LegDto(l.getKind().name(), l.getStatus().name(), l.getCurrency(),
                        l.getAmountMinor(), Money.of(l.getAmountMinor(), l.getCurrency()).toMajor().toPlainString()))
                .toList();
        List<SwiftDto> swiftDtos = swift.stream()
                .map(s -> new SwiftDto(s.getMessageType(), s.getUetr(), s.getReference(), s.getFinText()))
                .toList();
        Map<UUID, List<PostingDto>> byJv = postings.stream().collect(Collectors.groupingBy(
                Posting::getJvId,
                Collectors.mapping(
                        p -> new PostingDto(p.getAccountCode(), p.getDirection().name(), p.getAmountMinor()),
                        Collectors.toList())));
        List<JournalDto> journal = jvs.stream()
                .map(jv -> new JournalDto(jv.getMovementType(), jv.getCurrency(), jv.getAmountMinor(),
                        byJv.getOrDefault(jv.getJvId(), List.of())))
                .toList();
        return new TransferResponse(
                t.getTransferId(), t.getQuoteId(), t.getCorridor(), t.getStatus().name(),
                t.getSendCurrency(), Money.of(t.getSendAmountMinor(), t.getSendCurrency()).toMajor().toPlainString(),
                t.getReceiveCurrency(), Money.of(t.getReceiveAmountMinor(), t.getReceiveCurrency()).toMajor().toPlainString(),
                t.getQuotedRate(), Money.of(t.getTotalFeesMinor(), t.getSendCurrency()).toMajor().toPlainString(),
                t.getSenderName(), t.getRecipientName(), t.getRecipientAccount(), t.getRecipientBankBic(),
                t.getCreatedAt(), legDtos, swiftDtos, journal
        );
    }
}
