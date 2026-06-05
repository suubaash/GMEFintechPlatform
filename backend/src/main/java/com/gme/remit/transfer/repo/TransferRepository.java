package com.gme.remit.transfer.repo;

import com.gme.remit.transfer.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    List<Transfer> findTop20ByOrderByCreatedAtDesc();
}
