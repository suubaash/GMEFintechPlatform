package com.gme.remit.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "account")
public class Account {

    @Id
    @Column(name = "account_code")
    private String accountCode;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name = "account_role", nullable = false)
    private String accountRole;

    protected Account() {
    }

    public static Account of(String role, String currency) {
        Account a = new Account();
        a.currency = currency.toUpperCase();
        a.accountRole = role;
        a.accountType = ChartOfAccounts.typeOf(role);
        a.accountCode = ChartOfAccounts.accountCode(role, a.currency);
        return a;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getAccountRole() {
        return accountRole;
    }
}
