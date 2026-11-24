package ru.mirea.warehouse.application;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import ru.mirea.warehouse.domain.*;
import ru.mirea.warehouse.rules.*;

public final class StorageSelector {
    private final StorageRule conditionsRule;
    private final StorageRule zoneRule;
    public StorageSelector() {
        conditionsRule = new ConditionsRule();
        zoneRule = new ZoneRule();
    }
    /** Один проход O(k), дополнительная память O(1), первая ячейка при равенстве. */
    public Optional<StorageCell> selectStorageCell(Product product, BigInteger quantity, List<StorageCell> storageCells) {
        StorageCell bestCell = null;
        BigInteger bestRemaining = null;
        for (StorageCell cell : storageCells) {
            if (!"доступна".equals(cell.getStatus())) continue;
            if (!conditionsRule.isSatisfied(product, cell)) continue;
            if (!zoneRule.isSatisfied(product, cell)) continue;
            BigInteger remaining = cell.getAvailableCapacity().subtract(quantity);
            if (remaining.signum() < 0) continue;
            if (bestCell == null || remaining.compareTo(bestRemaining) < 0) {
                bestCell = cell;
                bestRemaining = remaining;
            }
        }
        return Optional.ofNullable(bestCell);
    }
}
