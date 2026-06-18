package org.example.market.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaMarketRepository extends JpaRepository<JpaMarket, Long> {

    List<JpaMarket> findAllByEnabledTrueOrderByIdAsc();
}
