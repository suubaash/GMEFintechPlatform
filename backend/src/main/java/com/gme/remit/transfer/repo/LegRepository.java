package com.gme.remit.transfer.repo;

import com.gme.remit.transfer.domain.Leg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LegRepository extends JpaRepository<Leg, UUID> {
    List<Leg> findByTransferIdOrderBySequence(UUID transferId);
}
