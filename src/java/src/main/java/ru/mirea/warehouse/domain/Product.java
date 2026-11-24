package ru.mirea.warehouse.domain;

import java.math.BigInteger;
import java.time.LocalDate;

public class Product {
    private String productId;
    private String name;
    private String sku;
    private String storageConditions;
    private String allowedZone;

    public Product(String productId, String name, String sku, String storageConditions, String allowedZone) {
        this.productId = productId;
        this.name = name;
        this.sku = sku;
        this.storageConditions = storageConditions;
        this.allowedZone = allowedZone;
    }
    public String getProductId() { return productId; }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public String getStorageConditions() { return storageConditions; }
    public String getAllowedZone() { return allowedZone; }

}
