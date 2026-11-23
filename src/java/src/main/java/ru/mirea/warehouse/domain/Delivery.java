package ru.mirea.warehouse.domain;

import java.math.BigInteger;
import java.time.LocalDate;

public class Delivery {
    private String deliveryId;
    private String clientId;
    private LocalDate expectedDate;
    private LocalDate actualDate;
    private String status;

    public Delivery(String deliveryId, String clientId, LocalDate expectedDate, LocalDate actualDate, String status) {
        this.deliveryId = deliveryId;
        this.clientId = clientId;
        this.expectedDate = expectedDate;
        this.actualDate = actualDate;
        this.status = status;
    }
    public String getDeliveryId() { return deliveryId; }
    public String getClientId() { return clientId; }
    public LocalDate getExpectedDate() { return expectedDate; }
    public LocalDate getActualDate() { return actualDate; }
    public String getStatus() { return status; }
    public void receive(LocalDate value) { actualDate = value; status = "поступила"; }
    public void setStatus(String value) { status = value; }
}
