package org.example.market.adapter.out.persistence;

import org.example.market.domain.model.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

public interface MarketJpaRepository extends JpaRepository<Market, Long> {

    List<Market> findAllByEnabledTrueOrderByIdAsc();
}
