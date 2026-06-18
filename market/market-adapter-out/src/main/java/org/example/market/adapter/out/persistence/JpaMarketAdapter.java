package org.example.market.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.example.market.application.port.out.MarketPersistencePort;
import org.example.market.application.service.command.ChangeMarketsCommand;
import org.example.market.application.service.command.CreateMarketCommand;
import org.example.market.application.service.command.DeleteMarketCommand;
import org.example.market.application.service.command.UpdateMarketCommand;
import org.example.market.domain.model.Market;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class JpaMarketAdapter implements MarketPersistencePort {

    private final JpaMarketRepository marketRepository;

    @Override
    public List<Market> findAllByEnabledTrueOrderByIdAsc() {
        return marketRepository.findAllByEnabledTrueOrderByIdAsc()
                .stream()
                .map(JpaMarket::toDomain)
                .toList();
    }

    @Override
    public void changeMarkets(ChangeMarketsCommand command) {
        deleteMarkets(command.deletes());
        updateMarkets(command.updates());
        createMarkets(command.creates());
    }

    private void createMarkets(List<CreateMarketCommand> commands) {
        if (commands.isEmpty()) {
            return;
        }

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

    private void updateMarkets(List<UpdateMarketCommand> commands) {
        if (commands.isEmpty()) {
            return;
        }

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

    private void deleteMarkets(List<DeleteMarketCommand> commands) {
        if (commands.isEmpty()) {
            return;
        }

        List<Long> ids = commands.stream()
                .map(DeleteMarketCommand::id)
                .toList();

        marketRepository.deleteAllByIdInBatch(ids);
    }
}