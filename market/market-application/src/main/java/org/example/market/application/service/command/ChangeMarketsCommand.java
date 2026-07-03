package org.example.market.application.service.command;

import java.util.List;

public record ChangeMarketsCommand(
        List<CreateMarketCommand> creates,
        List<UpdateMarketCommand> updates,
        List<DeleteMarketCommand> deletes
) {

    public ChangeMarketsCommand {
        creates = creates == null ? List.of() : List.copyOf(creates);
        updates = updates == null ? List.of() : List.copyOf(updates);
        deletes = deletes == null ? List.of() : List.copyOf(deletes);
    }

    public boolean isEmpty() {
        return creates.isEmpty() && updates.isEmpty() && deletes.isEmpty();
    }

    public boolean hasCreates() {
        return !creates.isEmpty();
    }

    public boolean hasUpdates() {
        return !updates.isEmpty();
    }

    public boolean hasDeletes() {
        return !deletes.isEmpty();
    }

    public List<Long> deleteIds() {
        return deletes.stream()
                .map(DeleteMarketCommand::id)
                .toList();
    }

    public record CreateMarketCommand(
            String marketCode,
            String symbol,
            String koreanName,
            String englishName,
            boolean enabled
    ) {
    }

    public record UpdateMarketCommand(
            Long id,
            String marketCode,
            String symbol,
            String koreanName,
            String englishName,
            boolean enabled
    ) {
    }

    public record DeleteMarketCommand(
            Long id
    ) {
    }
}