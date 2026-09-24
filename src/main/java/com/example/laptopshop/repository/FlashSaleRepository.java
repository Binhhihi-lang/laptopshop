package com.example.laptopshop.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.laptopshop.domain.FlashSale;

public interface FlashSaleRepository extends JpaRepository<FlashSale, String> {

    /** Phiên ĐANG diễn ra tại {@code now}, kết thúc sớm nhất trước (D31). */
    @Query("""
            SELECT f FROM FlashSale f
            WHERE f.active = true
              AND f.startAt <= :now
              AND f.endAt >= :now
            ORDER BY f.endAt ASC
            """)
    List<FlashSale> findActiveBetween(@Param("now") LocalDateTime now);

    /** Phiên SẮP tới — FE vẽ "Diễn ra lúc 19:00" (D31). */
    @Query("""
            SELECT f FROM FlashSale f
            WHERE f.active = true
              AND f.startAt > :now
            ORDER BY f.startAt ASC
            """)
    List<FlashSale> findUpcoming(@Param("now") LocalDateTime now);
}
