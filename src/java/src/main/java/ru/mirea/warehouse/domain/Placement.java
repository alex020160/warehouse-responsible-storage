package ru.mirea.warehouse.domain;

import java.math.BigInteger;
import java.time.LocalDate;

public class Placement {
    private String placementId;
    private String batchId;
    private String cellId;
    private BigInteger quantity;
    private LocalDate placementDate;

    public Placement(String placementId, String batchId, String cellId, BigInteger quantity, LocalDate placementDate) {
        this.placementId = placementId;
        this.batchId = batchId;
        this.cellId = cellId;
        this.quantity = quantity;
        this.placementDate = placementDate;
    }
    public String getPlacementId() { return placementId; }
    public String getBatchId() { return batchId; }
    public String getCellId() { return cellId; }
    public BigInteger getQuantity() { return quantity; }
    public LocalDate getPlacementDate() { return placementDate; }

}
