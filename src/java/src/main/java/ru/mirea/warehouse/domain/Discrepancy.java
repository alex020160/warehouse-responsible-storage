package ru.mirea.warehouse.domain;

import java.math.BigInteger;
import java.time.LocalDate;

public class Discrepancy {
    private String discrepancyId;
    private String deliveryItemId;
    private String discrepancyType;
    private BigInteger quantityDifference;
    private String description;

    public Discrepancy(String discrepancyId, String deliveryItemId, String discrepancyType, BigInteger quantityDifference, String description) {
        this.discrepancyId = discrepancyId;
        this.deliveryItemId = deliveryItemId;
        this.discrepancyType = discrepancyType;
        this.quantityDifference = quantityDifference;
        this.description = description;
    }
    public String getDiscrepancyId() { return discrepancyId; }
    public String getDeliveryItemId() { return deliveryItemId; }
    public String getDiscrepancyType() { return discrepancyType; }
    public BigInteger getQuantityDifference() { return quantityDifference; }
    public String getDescription() { return description; }
    public void validateAgainst(DeliveryItem item) {
        if (item.getActualQuantity() == null) throw new IllegalArgumentException("Расхождение требует факта поступления.");
        BigInteger difference = item.getActualQuantity().subtract(item.getExpectedQuantity());
        String expectedKind = item.getExpectedQuantity().signum() == 0 && item.getActualQuantity().signum() > 0
                ? "неожиданный товар" : difference.signum() < 0 ? "недостача" : difference.signum() > 0 ? "излишек" : null;
        if (!discrepancyType.equals(expectedKind) || !quantityDifference.equals(difference))
            throw new IllegalArgumentException("Вид и величина расхождения не соответствуют позиции.");
    }
}
