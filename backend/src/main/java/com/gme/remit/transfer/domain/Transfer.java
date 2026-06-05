package com.gme.remit.transfer.domain;

import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;
import com.gme.remit.quote.domain.Quote;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Customer-level money-movement object, created from a confirmed quote and driven through the SM. */
@Entity
@Table(name = "transfer")
public class Transfer {

    @Id
    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Column(name = "corridor", nullable = false)
    private String corridor;

    @Column(name = "send_currency", nullable = false)
    private String sendCurrency;

    @Column(name = "send_amount_minor", nullable = false)
    private long sendAmountMinor;

    @Column(name = "receive_currency", nullable = false)
    private String receiveCurrency;

    @Column(name = "receive_amount_minor", nullable = false)
    private long receiveAmountMinor;

    @Column(name = "quoted_rate", nullable = false)
    private BigDecimal quotedRate;

    @Column(name = "total_fees_minor", nullable = false)
    private long totalFeesMinor;

    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_account")
    private String recipientAccount;

    @Column(name = "recipient_bank_bic")
    private String recipientBankBic;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransferStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Transfer() {
    }

    public static Transfer fromQuote(Quote quote, String senderName, String recipientName,
                                     String recipientAccount, String recipientBankBic) {
        Transfer t = new Transfer();
        t.transferId = UUID.randomUUID();
        t.quoteId = quote.getQuoteId();
        t.corridor = quote.getCorridor();
        t.sendCurrency = quote.getSendCurrency();
        t.sendAmountMinor = quote.getSendAmountMinor();
        t.receiveCurrency = quote.getReceiveCurrency();
        t.receiveAmountMinor = quote.getReceiveAmountMinor();
        t.quotedRate = quote.getQuotedRate();
        t.totalFeesMinor = quote.getTotalFeesMinor();
        t.senderName = senderName;
        t.recipientName = recipientName;
        t.recipientAccount = recipientAccount;
        t.recipientBankBic = recipientBankBic;
        t.status = TransferStatus.SUBMITTED;
        OffsetDateTime now = OffsetDateTime.now();
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    public void transitionTo(TransferStatus to) {
        if (!status.canTransitionTo(to)) {
            throw new DomainException(ErrorCode.ILLEGAL_TRANSITION,
                    "transfer " + status + " -> " + to + " not allowed");
        }
        this.status = to;
        this.updatedAt = OffsetDateTime.now();
    }

    public long principalMinor() {
        return sendAmountMinor - totalFeesMinor;
    }

    public UUID getTransferId() { return transferId; }
    public UUID getQuoteId() { return quoteId; }
    public String getCorridor() { return corridor; }
    public String getSendCurrency() { return sendCurrency; }
    public long getSendAmountMinor() { return sendAmountMinor; }
    public String getReceiveCurrency() { return receiveCurrency; }
    public long getReceiveAmountMinor() { return receiveAmountMinor; }
    public BigDecimal getQuotedRate() { return quotedRate; }
    public long getTotalFeesMinor() { return totalFeesMinor; }
    public String getSenderName() { return senderName; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientAccount() { return recipientAccount; }
    public String getRecipientBankBic() { return recipientBankBic; }
    public TransferStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
