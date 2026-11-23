package ru.mirea.warehouse.domain;

import java.math.BigInteger;
import java.time.LocalDate;

public class Client {
    private String clientId;
    private String name;
    private String contactInfo;

    public Client(String clientId, String name, String contactInfo) {
        this.clientId = clientId;
        this.name = name;
        this.contactInfo = contactInfo;
    }
    public String getClientId() { return clientId; }
    public String getName() { return name; }
    public String getContactInfo() { return contactInfo; }

}
