package ru.mirea.warehouse.rules;

import ru.mirea.warehouse.domain.*;

public final class ConditionsRule implements StorageRule {
    @Override
    public boolean isSatisfied(Product product, StorageCell cell) {
        return product.getStorageConditions().equals(cell.getStorageConditions());
    }
}
