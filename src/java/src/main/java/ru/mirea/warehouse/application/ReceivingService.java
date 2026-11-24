package ru.mirea.warehouse.application;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import ru.mirea.warehouse.domain.*;
import ru.mirea.warehouse.infrastructure.IntegrityValidator;

public final class ReceivingService {
    private final Warehouse data;
    private final IntegrityValidator validator;
    private final DeliveryComparator comparator;
    private final StorageSelector selector;
    public ReceivingService(Warehouse data, IntegrityValidator validator) {
        this.data = data; this.validator = validator;
        comparator = new DeliveryComparator(); selector = new StorageSelector();
    }
    public List<DeliveryItem> deliveryItems(String id) {
        data.findDelivery(id);
        return data.getDeliveryItems().stream().filter(i -> i.getDeliveryId().equals(id)).toList();
    }
    private void validateItems(List<DeliveryItem> items, boolean actual, boolean allowEmpty) {
        validator.require(allowEmpty || !items.isEmpty(), "Нужен список позиций.");
        Set<String> seen = new HashSet<>(); Map<String, Product> products = data.productsById();
        for (DeliveryItem item : items) {
            validator.require(products.containsKey(item.getProductId()) && seen.add(item.getProductId()), "Неверный или повторный товар.");
            BigInteger quantity = actual ? item.getActualQuantity() : item.getExpectedQuantity();
            validator.require(quantity != null && quantity.signum() > 0, "Количество должно быть положительным.");
        }
    }
    public Delivery registerExpectedDelivery(String clientId, LocalDate expectedDate, List<DeliveryItem> items) {
        validator.validate(data); data.findClient(clientId); validator.require(expectedDate != null, "Укажите дату.");
        validator.parseDate(expectedDate.toString());
        validateItems(items, false, false);
        Delivery result = new Delivery(newId(data.getDeliveries(), Delivery::getDeliveryId, "D"), clientId, expectedDate, null, "ожидается");
        data.add(result);
        for (DeliveryItem i : items) data.add(new DeliveryItem(newId(data.getDeliveryItems(), DeliveryItem::getDeliveryItemId, "I"), result.getDeliveryId(), i.getProductId(), i.getExpectedQuantity(), null));
        validator.validate(data); return result;
    }
    public List<String> recordActualReceipt(String id, List<DeliveryItem> actualItems, LocalDate date) {
        validator.validate(data); Delivery delivery = data.findDelivery(id);
        validator.require(delivery.getStatus().equals("ожидается"), "Повторный ввод факта запрещён.");
        validator.require(date != null, "Укажите дату."); validator.parseDate(date.toString());
        validateItems(actualItems, true, true);
        List<DeliveryItem> items = new ArrayList<>(deliveryItems(id));
        for (DeliveryItem i : items) i.recordActual(BigInteger.ZERO);
        for (DeliveryItem actual : actualItems) {
            DeliveryItem found = null;
            for (DeliveryItem i : items) if (i.getProductId().equals(actual.getProductId())) { found = i; break; }
            if (found == null) {
                found = new DeliveryItem(newId(data.getDeliveryItems(), DeliveryItem::getDeliveryItemId, "I"), id, actual.getProductId(), BigInteger.ZERO, BigInteger.ZERO);
                data.add(found); items.add(found);
            }
            found.recordActual(actual.getActualQuantity());
        }
        delivery.receive(date); validator.validate(data);
        return items.stream().map(DeliveryItem::getDeliveryItemId).toList();
    }
    public List<Map<String, Object>> receiptDiscrepancies(String id) {
        List<DeliveryItem> items = deliveryItems(id);
        validator.require(items.stream().noneMatch(i -> i.getActualQuantity() == null), "Сначала сохраните поступление.");
        return comparator.compareDeliveryItems(items.stream().filter(i -> i.getExpectedQuantity().signum() > 0).toList(),
                items.stream().filter(i -> i.getActualQuantity().signum() > 0).toList());
    }
    public List<Discrepancy> registerDiscrepancies(String id, List<Map<String, Object>> deviations) {
        validator.validate(data); Delivery delivery = data.findDelivery(id);
        validator.require(Set.of("поступила", "проверена", "требует согласования").contains(delivery.getStatus()), "Неверный этап регистрации расхождений.");
        List<Map<String, Object>> quantitative = new ArrayList<>();
        List<DeliveryItem> items = deliveryItems(id); Set<String> itemIds = new HashSet<>();
        for (DeliveryItem i : items) itemIds.add(i.getDeliveryItemId());
        for (Map<String, Object> entry : deviations) {
            validator.require(items.stream().anyMatch(i -> i.getProductId().equals(entry.get("product_id"))), "Нет позиции для расхождения.");
            if ("качество".equals(entry.get("discrepancy_type"))) {
                validator.require(!entry.getOrDefault("description", "").toString().isBlank() && BigInteger.ZERO.equals(entry.get("quantity_difference")), "Неверное качественное отклонение.");
            } else {
                Map<String, Object> value = new LinkedHashMap<>();
                for (String key : List.of("product_id", "discrepancy_type", "quantity_difference")) value.put(key, entry.get(key));
                quantitative.add(value);
            }
        }
        validator.require(quantitative.equals(receiptDiscrepancies(id)), "Расхождения должны совпадать с алгоритмом.");
        data.removeDiscrepancies(itemIds); List<Discrepancy> result = new ArrayList<>();
        for (Map<String, Object> entry : deviations) {
            DeliveryItem item = items.stream().filter(i -> i.getProductId().equals(entry.get("product_id"))).findFirst().orElseThrow();
            String recordId = newId(data.getDiscrepancies(), Discrepancy::getDiscrepancyId, "R");
            Discrepancy record = "качество".equals(entry.get("discrepancy_type"))
                    ? new QualityDiscrepancy(recordId, item.getDeliveryItemId(), entry.get("description").toString())
                    : new Discrepancy(recordId, item.getDeliveryItemId(), entry.get("discrepancy_type").toString(), (BigInteger) entry.get("quantity_difference"), entry.getOrDefault("description", "").toString());
            data.add(record); result.add(record);
        }
        delivery.setStatus(result.isEmpty() ? "проверена" : "требует согласования"); validator.validate(data); return result;
    }
    public List<Batch> acceptDelivery(String id, Map<String, Batch> details, boolean approved) {
        validator.validate(data); Delivery delivery = data.findDelivery(id);
        validator.require(Set.of("проверена", "требует согласования").contains(delivery.getStatus()), "Сначала зарегистрируйте результаты приёмки.");
        validator.require(approved && !details.isEmpty(), "Нужно согласование и хотя бы одна партия.");
        List<DeliveryItem> items = deliveryItems(id);
        for (var entry : details.entrySet()) {
            DeliveryItem item = items.stream().filter(i -> i.getDeliveryItemId().equals(entry.getKey())).findFirst().orElseThrow(() -> new IllegalArgumentException("Нет позиции для принятия."));
            Batch detail = entry.getValue();
            validator.require(item.getActualQuantity() != null && item.getActualQuantity().signum() > 0 && detail.getQuantity() != null && detail.getQuantity().signum() > 0 && detail.getQuantity().compareTo(item.getActualQuantity()) <= 0, "Неверное принятое количество.");
            validator.require(detail.getBatchNumber() != null && !detail.getBatchNumber().isBlank(), "Нужен номер партии.");
            if (detail.getExpirationDate() != null) validator.parseDate(detail.getExpirationDate().toString());
        }
        List<Batch> result = new ArrayList<>();
        for (var entry : details.entrySet()) {
            Batch detail = entry.getValue();
            Batch batch = new Batch(newId(data.getBatches(), Batch::getBatchId, "B"), entry.getKey(), detail.getBatchNumber(), detail.getExpirationDate(), detail.getQuantity());
            data.add(batch); result.add(batch);
        }
        delivery.setStatus("принята"); validator.validate(data); return result;
    }
    public BigInteger batchRemaining(String id) {
        BigInteger placed = BigInteger.ZERO;
        for (Placement p : data.getPlacements()) if (p.getBatchId().equals(id)) placed = placed.add(p.getQuantity());
        return data.findBatch(id).getQuantity().subtract(placed);
    }
    public Optional<StorageCell> selectForBatch(String id) {
        Batch batch = data.findBatch(id); BigInteger quantity = batchRemaining(id);
        validator.require(quantity.signum() > 0, "Партия уже размещена.");
        Product product = data.findProduct(data.findDeliveryItem(batch.getDeliveryItemId()).getProductId());
        return selector.selectStorageCell(product, quantity, data.getStorageCells());
    }
    public Placement registerPlacement(Placement placement, String requiredZone) {
        validator.validate(data);
        validator.require(data.getPlacements().stream().noneMatch(p -> p.getPlacementId().equals(placement.getPlacementId())), "Повторное размещение.");
        Batch batch = data.findBatch(placement.getBatchId()); DeliveryItem item = data.findDeliveryItem(batch.getDeliveryItemId());
        Delivery delivery = data.findDelivery(item.getDeliveryId());
        validator.require(delivery.getStatus().equals("принята"), "Поставка должна быть принята.");
        validator.require(placement.getQuantity() != null && placement.getQuantity().signum() > 0 && placement.getQuantity().equals(batchRemaining(batch.getBatchId())), "Разместите всю оставшуюся партию.");
        validator.require(placement.getPlacementDate() != null && !placement.getPlacementDate().isBefore(delivery.getActualDate()), "Неверная дата размещения.");
        validator.parseDate(placement.getPlacementDate().toString());
        Product product = data.findProduct(item.getProductId());
        validator.require(requiredZone == null || product.getAllowedZone() == null || product.getAllowedZone().equals(requiredZone), "Противоречие ограничения зоны.");
        if (requiredZone != null) product = new Product(product.getProductId(), product.getName(), product.getSku(), product.getStorageConditions(), requiredZone);
        StorageCell cell = data.findStorageCell(placement.getCellId());
        validator.require(selector.selectStorageCell(product, placement.getQuantity(), List.of(cell)).isPresent(), "Ячейка не подходит.");
        data.add(placement); cell.occupy(placement.getQuantity());
        Set<String> itemIds = new HashSet<>(); for (DeliveryItem i : deliveryItems(item.getDeliveryId())) itemIds.add(i.getDeliveryItemId());
        if (data.getBatches().stream().filter(b -> itemIds.contains(b.getDeliveryItemId())).allMatch(b -> batchRemaining(b.getBatchId()).signum() == 0)) delivery.setStatus("размещена");
        validator.validate(data); return placement;
    }
    public <T> String newId(List<T> records, Function<T, String> key, String prefix) {
        Set<String> used = new HashSet<>(); for (T record : records) used.add(key.apply(record));
        int number = used.size() + 1;
        while (used.contains(prefix + String.format(Locale.ROOT, "%03d", number))) number++;
        return prefix + String.format(Locale.ROOT, "%03d", number);
    }
}
