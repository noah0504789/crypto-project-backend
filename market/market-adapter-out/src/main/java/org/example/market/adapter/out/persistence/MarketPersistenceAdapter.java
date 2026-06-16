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
public class MarketPersistenceAdapter implements MarketPersistencePort {

    private final MarketJpaRepository marketJpaRepository;

    @Override
    public List<Market> findAllByEnabledTrueOrderByIdAsc() {
        return marketJpaRepository.findAllByEnabledTrueOrderByIdAsc();
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

        List<Market> markets = commands.stream()
                .map(command -> Market.create(
                        command.marketCode(),
                        command.symbol(),
                        command.koreanName(),
                        command.englishName(),
                        command.enabled()
                ))
                .toList();

        marketJpaRepository.saveAll(markets);
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

        List<Market> markets = marketJpaRepository.findAllById(commandMap.keySet());

        if (markets.size() != commandMap.size()) {
            throw new IllegalArgumentException("Some markets to update were not found.");
        }

        for (Market market : markets) {
            UpdateMarketCommand command = commandMap.get(market.getId());

            market.change(
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

        marketJpaRepository.deleteAllByIdInBatch(ids);
    }
}