package com.gme.remit.quote.repo;

import com.gme.remit.quote.domain.Quote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {
}
