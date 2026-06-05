package com.gme.remit.ledger.repo;

import com.gme.remit.ledger.domain.Direction;
import com.gme.remit.ledger.domain.Posting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostingRepository extends JpaRepository<Posting, java.util.UUID> {

    @Query("select coalesce(sum(p.amountMinor), 0) from Posting p "
            + "where p.accountCode = :code and p.direction = :dir")
    long sumByAccountAndDirection(@Param("code") String code, @Param("dir") Direction dir);

    @Query("select coalesce(sum(p.amountMinor), 0) from Posting p "
            + "where p.accountCode = :code and p.direction = :dir and p.cleared = true")
    long sumClearedByAccountAndDirection(@Param("code") String code, @Param("dir") Direction dir);

    @Query("select p from Posting p join JournalVoucher j on p.jvId = j.jvId "
            + "where p.accountCode = :code order by j.postedAt, p.postingId")
    List<Posting> statementFor(@Param("code") String code);
}
