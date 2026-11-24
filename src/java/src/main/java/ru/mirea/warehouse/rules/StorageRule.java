package ru.mirea.warehouse.rules;

import ru.mirea.warehouse.domain.Product;
import ru.mirea.warehouse.domain.StorageCell;

public interface StorageRule {
    boolean isSatisfied(Product product, StorageCell cell);
}
