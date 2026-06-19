package org.example.market.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface JpaMarketRepository extends JpaRepository<JpaMarket, Long> {

    List<JpaMarket> findAllByEnabledTrueOrderByIdAsc();

    List<JpaMarket> findAllByIdInAndEnabledTrue(Set<Long> ids);

    List<JpaMarket> findAllByMarketCodeInAndEnabledTrue(Set<String> marketCodes);
}