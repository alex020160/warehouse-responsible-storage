package ru.mirea.warehouse.domain;

import java.math.BigInteger;

/** Качественное отклонение отличается правилом проверки, но сохраняет схему ER. */
public final class QualityDiscrepancy extends Discrepancy {
    public QualityDiscrepancy(String id, String itemId, String description) {
        super(id, itemId, "качество", BigInteger.ZERO, description);
    }
    @Override
    public void validateAgainst(DeliveryItem item) {
        if (item.getActualQuantity() == null || !BigInteger.ZERO.equals(getQuantityDifference())
                || getDescription() == null || getDescription().isBlank())
            throw new IllegalArgumentException("Качественное отклонение требует описания и нулевой разности.");
    }
}
