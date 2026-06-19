package org.example.market.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface JpaPriceAlertSettingRepository extends JpaRepository<JpaPriceAlertSetting, Long> {

    @Query("""
            select s
            from JpaPriceAlertSetting s
            join fetch s.market m
            where s.userPublicId = :userPublicId
            """)
    List<JpaPriceAlertSetting> findAllByUserPublicIdWithMarket(
            @Param("userPublicId") UUID userPublicId
    );

    @Query("""
            select s
            from JpaPriceAlertSetting s
            join fetch s.market m
            where s.userPublicId = :userPublicId
              and m.marketCode in :marketCodes
            """)
    List<JpaPriceAlertSetting> findAllByUserPublicIdAndMarketCodeIn(
            @Param("userPublicId") UUID userPublicId,
            @Param("marketCodes") Set<String> marketCodes
    );
}