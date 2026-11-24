package ru.mirea.warehouse.domain;

import java.math.BigInteger;
import java.time.LocalDate;

public class DeliveryItem {
    private String deliveryItemId;
    private String deliveryId;
    private String productId;
    private BigInteger expectedQuantity;
    private BigInteger actualQuantity;

    public DeliveryItem(String deliveryItemId, String deliveryId, String productId, BigInteger expectedQuantity, BigInteger actualQuantity) {
        this.deliveryItemId = deliveryItemId;
        this.deliveryId = deliveryId;
        this.productId = productId;
        this.expectedQuantity = expectedQuantity;
        this.actualQuantity = actualQuantity;
    }
    public String getDeliveryItemId() { return deliveryItemId; }
    public String getDeliveryId() { return deliveryId; }
    public String getProductId() { return productId; }
    public BigInteger getExpectedQuantity() { return expectedQuantity; }
    public BigInteger getActualQuantity() { return actualQuantity; }
    public void recordActual(BigInteger value) { actualQuantity = value; }
}
