package com.gme.remit.quote.api;

import com.gme.remit.quote.api.dto.QuoteRequest;
import com.gme.remit.quote.api.dto.QuoteResponse;
import com.gme.remit.quote.domain.Corridor;
import com.gme.remit.quote.domain.CorridorCatalog;
import com.gme.remit.quote.service.QuoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class QuoteController {

    private final QuoteService quoteService;
    private final CorridorCatalog corridors;

    public QuoteController(QuoteService quoteService, CorridorCatalog corridors) {
        this.quoteService = quoteService;
        this.corridors = corridors;
    }

    @GetMapping("/corridors")
    public List<Corridor> corridors() {
        return corridors.all();
    }

    @PostMapping("/quotes")
    public ResponseEntity<QuoteResponse> create(@Valid @RequestBody QuoteRequest req) {
        var quote = quoteService.createQuote(req.corridor(), req.sendAmountMinor());
        return ResponseEntity.status(HttpStatus.CREATED).body(QuoteResponse.from(quote));
    }

    @GetMapping("/quotes/{id}")
    public QuoteResponse get(@PathVariable("id") UUID id) {
        return QuoteResponse.from(quoteService.getQuote(id));
    }
}
