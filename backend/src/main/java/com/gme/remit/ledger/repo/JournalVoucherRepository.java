package com.gme.remit.ledger.repo;

import com.gme.remit.ledger.domain.JournalVoucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalVoucherRepository extends JpaRepository<JournalVoucher, UUID> {

    List<JournalVoucher> findByTransferIdOrderByPostedAt(UUID transferId);
}
