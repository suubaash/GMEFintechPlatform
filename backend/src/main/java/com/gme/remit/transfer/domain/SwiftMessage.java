package com.gme.remit.transfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A generated SWIFT FIN message persisted against the transfer/payout leg. */
@Entity
@Table(name = "swift_message")
public class SwiftMessage {

    @Id
    @Column(name = "swift_id")
    private UUID swiftId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "leg_id")
    private UUID legId;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @Column(name = "uetr", nullable = false)
    private String uetr;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "fin_text", nullable = false)
    private String finText;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SwiftMessage() {
    }

    public static SwiftMessage of(UUID transferId, UUID legId, String messageType,
                                  String uetr, String reference, String finText) {
        SwiftMessage m = new SwiftMessage();
        m.swiftId = UUID.randomUUID();
        m.transferId = transferId;
        m.legId = legId;
        m.messageType = messageType;
        m.uetr = uetr;
        m.reference = reference;
        m.finText = finText;
        m.createdAt = OffsetDateTime.now();
        return m;
    }

    public UUID getSwiftId() { return swiftId; }
    public UUID getTransferId() { return transferId; }
    public UUID getLegId() { return legId; }
    public String getMessageType() { return messageType; }
    public String getUetr() { return uetr; }
    public String getReference() { return reference; }
    public String getFinText() { return finText; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
