package com.gme.remit.transfer.repo;

import com.gme.remit.transfer.domain.SwiftMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SwiftMessageRepository extends JpaRepository<SwiftMessage, UUID> {
    List<SwiftMessage> findByTransferIdOrderByCreatedAt(UUID transferId);
}
