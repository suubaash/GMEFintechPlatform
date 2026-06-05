package com.gme.remit.ledger.repo;

import com.gme.remit.ledger.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
}
