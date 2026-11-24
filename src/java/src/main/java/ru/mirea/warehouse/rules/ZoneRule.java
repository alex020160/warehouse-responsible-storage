package ru.mirea.warehouse.rules;

import ru.mirea.warehouse.domain.*;

public final class ZoneRule implements StorageRule {
    @Override
    public boolean isSatisfied(Product product, StorageCell cell) {
        return product.getAllowedZone() == null || product.getAllowedZone().equals(cell.getZone());
    }
}
