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
}