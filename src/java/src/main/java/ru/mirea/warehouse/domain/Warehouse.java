package ru.mirea.warehouse.domain;

import java.util.*;
import java.util.function.Function;

/** Агрегирует коллекции; отдельные записи сохраняют собственные идентификаторы. */
public final class Warehouse {
    private List<Client> clients = new ArrayList<>();
    private List<Delivery> deliveries = new ArrayList<>();
    private List<Product> products = new ArrayList<>();
    private List<DeliveryItem> deliveryItems = new ArrayList<>();
    private List<Batch> batches = new ArrayList<>();
    private List<Discrepancy> discrepancies = new ArrayList<>();
    private List<StorageCell> storageCells = new ArrayList<>();
    private List<Placement> placements = new ArrayList<>();
    public List<Client> getClients() { return Collections.unmodifiableList(clients); }
    public void add(Client value) { clients.add(value); }
    public Client findClient(String id) { return find(clients, Client::getClientId, id); }
    public List<Delivery> getDeliveries() { return Collections.unmodifiableList(deliveries); }
    public void add(Delivery value) { deliveries.add(value); }
    public Delivery findDelivery(String id) { return find(deliveries, Delivery::getDeliveryId, id); }
    public List<Product> getProducts() { return Collections.unmodifiableList(products); }
    public void add(Product value) { products.add(value); }
    public Product findProduct(String id) { return find(products, Product::getProductId, id); }
    public List<DeliveryItem> getDeliveryItems() { return Collections.unmodifiableList(deliveryItems); }
    public void add(DeliveryItem value) { deliveryItems.add(value); }
    public DeliveryItem findDeliveryItem(String id) { return find(deliveryItems, DeliveryItem::getDeliveryItemId, id); }
    public List<Batch> getBatches() { return Collections.unmodifiableList(batches); }
    public void add(Batch value) { batches.add(value); }
    public Batch findBatch(String id) { return find(batches, Batch::getBatchId, id); }
    public List<Discrepancy> getDiscrepancies() { return Collections.unmodifiableList(discrepancies); }
    public void add(Discrepancy value) { discrepancies.add(value); }
    public Discrepancy findDiscrepancy(String id) { return find(discrepancies, Discrepancy::getDiscrepancyId, id); }
    public List<StorageCell> getStorageCells() { return Collections.unmodifiableList(storageCells); }
    public void add(StorageCell value) { storageCells.add(value); }
    public StorageCell findStorageCell(String id) { return find(storageCells, StorageCell::getCellId, id); }
    public List<Placement> getPlacements() { return Collections.unmodifiableList(placements); }
    public void add(Placement value) { placements.add(value); }
    public Placement findPlacement(String id) { return find(placements, Placement::getPlacementId, id); }
    public Map<String, Product> productsById() {
        Map<String, Product> result = new LinkedHashMap<>();
        for (Product value : products) result.put(value.getProductId(), value);
        return Collections.unmodifiableMap(result);
    }
    public void removeDiscrepancies(Set<String> itemIds) {
        discrepancies.removeIf(d -> itemIds.contains(d.getDeliveryItemId()));
    }
    private <T> T find(List<T> records, Function<T, String> key, String id) {
        for (T record : records) if (Objects.equals(key.apply(record), id)) return record;
        throw new IllegalArgumentException("Не найдена запись: " + id);
    }
}
