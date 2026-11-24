package ru.mirea.warehouse.domain;

import java.math.BigInteger;
import java.time.LocalDate;

public class Batch {
    private String batchId;
    private String deliveryItemId;
    private String batchNumber;
    private LocalDate expirationDate;
    private BigInteger quantity;

    public Batch(String batchId, String deliveryItemId, String batchNumber, LocalDate expirationDate, BigInteger quantity) {
        this.batchId = batchId;
        this.deliveryItemId = deliveryItemId;
        this.batchNumber = batchNumber;
        this.expirationDate = expirationDate;
        this.quantity = quantity;
    }
    public String getBatchId() { return batchId; }
    public String getDeliveryItemId() { return deliveryItemId; }
    public String getBatchNumber() { return batchNumber; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public BigInteger getQuantity() { return quantity; }

}
