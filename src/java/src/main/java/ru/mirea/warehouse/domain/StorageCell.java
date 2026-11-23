package ru.mirea.warehouse.domain;

import java.math.BigInteger;
import java.time.LocalDate;

public class StorageCell {
    private String cellId;
    private String zone;
    private BigInteger capacity;
    private BigInteger availableCapacity;
    private String storageConditions;
    private String status;

    public StorageCell(String cellId, String zone, BigInteger capacity, BigInteger availableCapacity, String storageConditions, String status) {
        this.cellId = cellId;
        this.zone = zone;
        this.capacity = capacity;
        this.availableCapacity = availableCapacity;
        this.storageConditions = storageConditions;
        this.status = status;
    }
    public String getCellId() { return cellId; }
    public String getZone() { return zone; }
    public BigInteger getCapacity() { return capacity; }
    public BigInteger getAvailableCapacity() { return availableCapacity; }
    public String getStorageConditions() { return storageConditions; }
    public String getStatus() { return status; }
    public void occupy(BigInteger quantity) { availableCapacity = availableCapacity.subtract(quantity); }
}
