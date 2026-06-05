package com.gme.remit.ledger.service;

import com.gme.remit.common.money.Money;
import com.gme.remit.ledger.domain.Account;
import com.gme.remit.ledger.domain.BalanceType;
import com.gme.remit.ledger.domain.ChartOfAccounts;
import com.gme.remit.ledger.domain.Direction;
import com.gme.remit.ledger.domain.JournalVoucher;
import com.gme.remit.ledger.domain.Posting;
import com.gme.remit.ledger.repo.AccountRepository;
import com.gme.remit.ledger.repo.JournalVoucherRepository;
import com.gme.remit.ledger.repo.PostingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Double-entry operational ledger: append-only balanced JVs, typed balances, statements. */
@Service
public class LedgerService {

    private final AccountRepository accounts;
    private final JournalVoucherRepository jvs;
    private final PostingRepository postings;

    public LedgerService(AccountRepository accounts, JournalVoucherRepository jvs, PostingRepository postings) {
        this.accounts = accounts;
        this.jvs = jvs;
        this.postings = postings;
    }

    /** Idempotently create (if missing) the {@code ROLE-CCY} account and return its code. */
    @Transactional
    public String ensureAccount(String role, String currency) {
        String code = ChartOfAccounts.accountCode(role, currency);
        if (!accounts.existsById(code)) {
            accounts.save(Account.of(role, currency));
        }
        return code;
    }

    /** Post a balanced journal voucher. Validates each account exists and matches the JV currency. */
    @Transactional
    public JournalVoucher postJv(String movementType, String currency, List<JournalVoucher.Line> lines,
                                 UUID transferId, UUID legId, LocalDate valueDate, boolean cleared) {
        String ccy = currency.toUpperCase();
        for (JournalVoucher.Line l : lines) {
            Account acct = accounts.findById(l.accountCode())
                    .orElseThrow(() -> new IllegalArgumentException("account " + l.accountCode() + " does not exist"));
            if (!acct.getCurrency().equals(ccy)) {
                throw new IllegalArgumentException(
                        "account " + l.accountCode() + " currency != JV currency " + ccy);
            }
        }
        LocalDate vd = valueDate != null ? valueDate : LocalDate.now();
        JournalVoucher jv = JournalVoucher.create(movementType, ccy, lines, transferId, legId, vd, cleared);
        jvs.save(jv);
        postings.saveAll(jv.getPostings()); // append-only insert; each posting already carries its jv_id
        return jv;
    }

    @Transactional(readOnly = true)
    public List<Posting> postingsForTransfer(UUID transferId) {
        return postings.findByTransferIdOrderByJvId(transferId);
    }

    @Transactional(readOnly = true)
    public Map<BalanceType, Money> balance(String accountCode) {
        Account acct = accounts.findById(accountCode)
                .orElseThrow(() -> new IllegalArgumentException("account " + accountCode + " does not exist"));
        String ccy = acct.getCurrency();
        Direction normal = acct.getAccountType().normalSide();

        long debit = postings.sumByAccountAndDirection(accountCode, Direction.DEBIT);
        long credit = postings.sumByAccountAndDirection(accountCode, Direction.CREDIT);
        long ledger = normal == Direction.DEBIT ? debit - credit : credit - debit;

        long dCleared = postings.sumClearedByAccountAndDirection(accountCode, Direction.DEBIT);
        long cCleared = postings.sumClearedByAccountAndDirection(accountCode, Direction.CREDIT);
        long cleared = normal == Direction.DEBIT ? dCleared - cCleared : cCleared - dCleared;

        Map<BalanceType, Money> out = new EnumMap<>(BalanceType.class);
        out.put(BalanceType.LEDGER, Money.of(ledger, ccy));
        out.put(BalanceType.AVAILABLE, Money.of(ledger, ccy)); // reservations not modelled in golden path
        out.put(BalanceType.CLEARED, Money.of(cleared, ccy));
        return out;
    }

    @Transactional(readOnly = true)
    public List<Posting> statement(String accountCode) {
        return postings.statementFor(accountCode);
    }

    @Transactional(readOnly = true)
    public List<JournalVoucher> journalForTransfer(UUID transferId) {
        return jvs.findByTransferIdOrderByPostedAt(transferId);
    }
}
