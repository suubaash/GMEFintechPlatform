package com.gme.remit.transfer.api;

import com.gme.remit.ledger.service.LedgerService;
import com.gme.remit.transfer.api.dto.TransferRequest;
import com.gme.remit.transfer.api.dto.TransferResponse;
import com.gme.remit.transfer.domain.Transfer;
import com.gme.remit.transfer.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;
    private final LedgerService ledger;

    public TransferController(TransferService transferService, LedgerService ledger) {
        this.transferService = transferService;
        this.ledger = ledger;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(@Valid @RequestBody TransferRequest req) {
        Transfer t = transferService.execute(req.quoteId(), req.senderName(), req.recipientName(),
                req.recipientAccount(), req.recipientBankBic());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(t.getTransferId()));
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable("id") UUID id) {
        transferService.get(id); // 404 if missing
        return view(id);
    }

    private TransferResponse view(UUID id) {
        Transfer t = transferService.get(id);
        return TransferResponse.from(t,
                transferService.legsOf(id),
                transferService.swiftOf(id),
                ledger.journalForTransfer(id));
    }
}
