package com.gme.remit.identity.repo;

import com.gme.remit.identity.domain.Party;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PartyRepository extends JpaRepository<Party, UUID> {

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<Party> findByEmail(String email);

    Optional<Party> findByPhone(String phone);
}
