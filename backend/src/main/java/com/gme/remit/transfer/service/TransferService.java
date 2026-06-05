package com.gme.remit.transfer.service;

import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;
import com.gme.remit.common.money.Money;
import com.gme.remit.ledger.domain.Direction;
import com.gme.remit.ledger.domain.JournalVoucher;
import com.gme.remit.ledger.service.LedgerService;
import com.gme.remit.quote.domain.Corridor;
import com.gme.remit.quote.domain.CorridorCatalog;
import com.gme.remit.quote.domain.Quote;
import com.gme.remit.quote.service.QuoteService;
import com.gme.remit.swift.Mt103;
import com.gme.remit.swift.SwiftCustomer;
import com.gme.remit.swift.SwiftInstitution;
import com.gme.remit.transfer.domain.Leg;
import com.gme.remit.transfer.domain.LegKind;
import com.gme.remit.transfer.domain.LegStatus;
import com.gme.remit.transfer.domain.MovementType;
import com.gme.remit.transfer.domain.SwiftMessage;
import com.gme.remit.transfer.domain.Transfer;
import com.gme.remit.transfer.domain.TransferStatus;
import com.gme.remit.transfer.repo.LegRepository;
import com.gme.remit.transfer.repo.SwiftMessageRepository;
import com.gme.remit.transfer.repo.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Golden-path orchestration: confirm quote → create transfer + legs → drive the state machines while
 * posting balanced double-entry JVs at each step → generate the SWIFT MT103 for the payout leg.
 */
@Service
public class TransferService {

    /** Operator (hub) BIC used as the SWIFT sender. */
    private static final String OPERATOR_BIC = "GMEBKRSEXXX";

    private final QuoteService quoteService;
    private final CorridorCatalog corridors;
    private final LedgerService ledger;
    private final TransferRepository transfers;
    private final LegRepository legs;
    private final SwiftMessageRepository swiftMessages;

    public TransferService(QuoteService quoteService, CorridorCatalog corridors, LedgerService ledger,
                           TransferRepository transfers, LegRepository legs,
                           SwiftMessageRepository swiftMessages) {
        this.quoteService = quoteService;
        this.corridors = corridors;
        this.ledger = ledger;
        this.transfers = transfers;
        this.legs = legs;
        this.swiftMessages = swiftMessages;
    }

    @Transactional
    public Transfer execute(UUID quoteId, String senderName, String recipientName,
                            String recipientAccount, String recipientBankBic) {
        Quote quote = quoteService.confirmQuote(quoteId);
        Corridor corridor = corridors.require(quote.getCorridor());
        String payoutBic = (recipientBankBic != null && !recipientBankBic.isBlank())
                ? recipientBankBic.trim() : corridor.payoutBic();

        Transfer t = transfers.save(
                Transfer.fromQuote(quote, senderName, recipientName, recipientAccount, payoutBic));

        Leg payin = legs.save(Leg.create(t.getTransferId(), LegKind.PAYIN,
                t.getSendCurrency(), t.getSendAmountMinor(), 1));
        Leg conversion = legs.save(Leg.create(t.getTransferId(), LegKind.CONVERSION,
                t.getSendCurrency(), t.principalMinor(), 2));
        Leg payout = legs.save(Leg.create(t.getTransferId(), LegKind.PAYOUT,
                t.getReceiveCurrency(), t.getReceiveAmountMinor(), 3));

        String send = t.getSendCurrency();
        String recv = t.getReceiveCurrency();
        // Seed the accounts this corridor touches.
        String nostroSend = ledger.ensureAccount("OPERATOR_NOSTRO", send);
        String wallet = ledger.ensureAccount("CUSTOMER_WALLET", send);
        String feeIncome = ledger.ensureAccount("FEE_INCOME", send);
        String nostroRecv = ledger.ensureAccount("OPERATOR_NOSTRO", recv);
        String inTransit = ledger.ensureAccount("IN_TRANSIT_PAYOUT", recv);
        String payoutCost = ledger.ensureAccount("PARTNER_PAYOUT_COST", recv);

        LocalDate vd = LocalDate.now();
        UUID id = t.getTransferId();

        // (1) PAYIN — funds received: hold customer money as a liability.
        advance(payin, LegStatus.IN_FLIGHT, LegStatus.CONFIRMED, LegStatus.SETTLED);
        t.transitionTo(TransferStatus.FUNDS_RECEIVED);
        ledger.postJv(MovementType.PAYIN.name(), send, List.of(
                new JournalVoucher.Line(nostroSend, Direction.DEBIT, t.getSendAmountMinor()),
                new JournalVoucher.Line(wallet, Direction.CREDIT, t.getSendAmountMinor())
        ), id, payin.getLegId(), vd, true);

        // (2) FEE — capture revenue from the wallet.
        if (t.getTotalFeesMinor() > 0) {
            ledger.postJv(MovementType.FEE.name(), send, List.of(
                    new JournalVoucher.Line(wallet, Direction.DEBIT, t.getTotalFeesMinor()),
                    new JournalVoucher.Line(feeIncome, Direction.CREDIT, t.getTotalFeesMinor())
            ), id, payin.getLegId(), vd, true);
        }

        // (3) PROCESSING + CONVERSION — move principal out of the wallet into the hub (sell KRW).
        t.transitionTo(TransferStatus.PROCESSING);
        advance(conversion, LegStatus.IN_FLIGHT, LegStatus.CONFIRMED, LegStatus.SETTLED);
        ledger.postJv(MovementType.CONVERSION_SELL.name(), send, List.of(
                new JournalVoucher.Line(wallet, Direction.DEBIT, t.principalMinor()),
                new JournalVoucher.Line(nostroSend, Direction.CREDIT, t.principalMinor())
        ), id, conversion.getLegId(), vd, true);

        // (4) PAYOUT_INITIATED — fund the payout (spend receive ccy) and emit the SWIFT MT103.
        t.transitionTo(TransferStatus.PAYOUT_INITIATED);
        payout.transitionTo(LegStatus.IN_FLIGHT);
        ledger.postJv(MovementType.PAYOUT.name(), recv, List.of(
                new JournalVoucher.Line(inTransit, Direction.DEBIT, t.getReceiveAmountMinor()),
                new JournalVoucher.Line(nostroRecv, Direction.CREDIT, t.getReceiveAmountMinor())
        ), id, payout.getLegId(), vd, false);
        generateMt103(t, corridor, payout, payoutBic, vd);

        // (5) COMPLETED — partner pays the beneficiary; clear in-transit, book payout cost.
        payout.transitionTo(LegStatus.CONFIRMED);
        payout.transitionTo(LegStatus.SETTLED);
        t.transitionTo(TransferStatus.COMPLETED);
        ledger.postJv(MovementType.SETTLEMENT.name(), recv, List.of(
                new JournalVoucher.Line(payoutCost, Direction.DEBIT, t.getReceiveAmountMinor()),
                new JournalVoucher.Line(inTransit, Direction.CREDIT, t.getReceiveAmountMinor())
        ), id, payout.getLegId(), vd, true);

        return t;
    }

    private void advance(Leg leg, LegStatus... path) {
        for (LegStatus s : path) {
            leg.transitionTo(s);
        }
    }

    private void generateMt103(Transfer t, Corridor corridor, Leg payout, String payoutBic, LocalDate vd) {
        String reference = t.getTransferId().toString().replace("-", "").substring(0, 16);
        Money receive = Money.of(t.getReceiveAmountMinor(), t.getReceiveCurrency());
        Mt103 mt = Mt103.builder()
                .sender(OPERATOR_BIC)
                .receiver(payoutBic)
                .reference(reference)
                .valueDate(vd)
                .money(t.getReceiveCurrency(), receive.toMajor())
                .ordering(SwiftCustomer.of(t.getSenderName(), null, "KR"))
                .beneficiary(SwiftCustomer.of(t.getRecipientName(),
                        t.getRecipientAccount(), corridor.payoutCountry()))
                .accountWith(SwiftInstitution.ofBic(payoutBic, corridor.payoutBankName()))
                .remittance("REMITTANCE " + reference);
        String fin = mt.build();
        swiftMessages.save(SwiftMessage.of(t.getTransferId(), payout.getLegId(),
                "MT103", mt.getUetr(), reference, fin));
    }

    @Transactional(readOnly = true)
    public Transfer get(UUID transferId) {
        return transfers.findById(transferId)
                .orElseThrow(() -> new DomainException(ErrorCode.TRANSFER_NOT_FOUND, "transfer not found"));
    }

    @Transactional(readOnly = true)
    public List<Leg> legsOf(UUID transferId) {
        return legs.findByTransferIdOrderBySequence(transferId);
    }

    @Transactional(readOnly = true)
    public List<SwiftMessage> swiftOf(UUID transferId) {
        return swiftMessages.findByTransferIdOrderByCreatedAt(transferId);
    }

    @Transactional(readOnly = true)
    public List<Transfer> recent() {
        return transfers.findTop20ByOrderByCreatedAtDesc();
    }
}
