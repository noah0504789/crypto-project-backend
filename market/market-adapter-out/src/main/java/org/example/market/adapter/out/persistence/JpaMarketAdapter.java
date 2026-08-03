package org.example.market.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.example.market.application.port.out.MarketPersistencePort;
import org.example.market.application.service.command.ChangeMarketsCommand.CreateMarketCommand;
import org.example.market.application.service.command.ChangeMarketsCommand.UpdateMarketCommand;
import org.example.market.domain.model.Market;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class JpaMarketAdapter implements MarketPersistencePort {

    private final JpaMarketRepository marketRepository;

    @Override
    public List<Market> findAllEnabledOrderByIdAsc() {
        return marketRepository.findAllByEnabledTrueOrderByIdAsc()
                .stream()
                .map(JpaMarket::toDomain)
                .toList();
    }

    @Override
    public List<Market> findAllEnabledByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return marketRepository.findAllByIdInAndEnabledTrue(ids)
                .stream()
                .map(JpaMarket::toDomain)
                .toList();
    }

    @Override
    public void createMarkets(List<CreateMarketCommand> commands) {
        List<JpaMarket> markets = commands.stream()
                .map(command -> JpaMarket.create(
                        command.marketCode(),
                        command.symbol(),
                        command.koreanName(),
                        command.englishName(),
                        command.enabled()
                ))
                .toList();

        marketRepository.saveAll(markets);
    }

    @Override
    public void updateMarkets(List<UpdateMarketCommand> commands) {
        Map<Long, UpdateMarketCommand> commandMap = commands.stream()
                .collect(Collectors.toMap(
                        UpdateMarketCommand::id,
                        Function.identity()
                ));

        List<JpaMarket> jpaMarkets = marketRepository.findAllById(commandMap.keySet());

        if (jpaMarkets.size() != commandMap.size()) {
            throw new IllegalArgumentException("Some markets to update were not found.");
        }

        for (JpaMarket jpaMarket : jpaMarkets) {
            UpdateMarketCommand command = commandMap.get(jpaMarket.getId());

            jpaMarket.update(
                    command.marketCode(),
                    command.symbol(),
                    command.koreanName(),
                    command.englishName(),
                    command.enabled()
            );
        }
    }

    @Override
    public void deleteMarketsByIds(List<Long> marketIds) {
        marketRepository.deleteAllByIdInBatch(marketIds);
    }
}