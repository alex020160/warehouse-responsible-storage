package ru.mirea.warehouse.infrastructure;

import com.google.gson.*;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import ru.mirea.warehouse.domain.*;

/** Проверяет одну и ту же схему при чтении, изменении и сохранении. */
public final class IntegrityValidator {
    private final Gson gson;
    private final Map<String, List<String>> fields;
    public IntegrityValidator(Gson gson) {
        this.gson = gson;
        fields = new LinkedHashMap<>();
        fields.put("clients", List.of("client_id", "name", "contact_info"));
        fields.put("deliveries", List.of("delivery_id", "client_id", "expected_date", "actual_date", "status"));
        fields.put("products", List.of("product_id", "name", "sku", "storage_conditions", "allowed_zone"));
        fields.put("delivery_items", List.of("delivery_item_id", "delivery_id", "product_id", "expected_quantity", "actual_quantity"));
        fields.put("batches", List.of("batch_id", "delivery_item_id", "batch_number", "expiration_date", "quantity"));
        fields.put("discrepancies", List.of("discrepancy_id", "delivery_item_id", "discrepancy_type", "quantity_difference", "description"));
        fields.put("storage_cells", List.of("cell_id", "zone", "capacity", "available_capacity", "storage_conditions", "status"));
        fields.put("placements", List.of("placement_id", "batch_id", "cell_id", "quantity", "placement_date"));
    }
    public void validateDocument(JsonObject document) {
        require(document.keySet().equals(fields.keySet()), "Нужны восемь коллекций ER.");
        for (var collection : fields.entrySet()) {
            JsonElement entries = document.get(collection.getKey());
            require(entries.isJsonArray(), "Коллекция должна быть списком.");
            Set<String> ids = new HashSet<>();
            for (JsonElement entry : entries.getAsJsonArray()) {
                require(entry.isJsonObject(), "Запись должна быть объектом.");
                JsonObject value = entry.getAsJsonObject();
                require(value.keySet().equals(new HashSet<>(collection.getValue())), "Поля не соответствуют ER.");
                for (String key : collection.getValue()) {
                    JsonElement v = value.get(key);
                    if (v.isJsonNull() && Set.of("actual_date", "expiration_date", "actual_quantity", "allowed_zone").contains(key)) continue;
                    require(v.isJsonPrimitive(), "Неверный тип поля: " + key);
                    JsonPrimitive primitive = v.getAsJsonPrimitive();
                    if (Set.of("quantity", "capacity", "available_capacity", "expected_quantity", "actual_quantity", "quantity_difference").contains(key)) {
                        require(primitive.isNumber() && primitive.getAsString().matches("-?(0|[1-9][0-9]*)"), "Требуется целое число.");
                        BigInteger number = primitive.getAsBigInteger();
                        if (!key.equals("quantity_difference")) require(number.compareTo(Set.of("quantity", "capacity").contains(key) ? BigInteger.ONE : BigInteger.ZERO) >= 0, "Неверное количество.");
                    } else {
                        require(primitive.isString(), "Требуется строка: " + key);
                        require(key.equals("description") || !primitive.getAsString().isBlank(), "Пустое поле: " + key);
                        if (key.endsWith("_date")) parseDate(primitive.getAsString());
                    }
                }
                String id = value.get(collection.getValue().getFirst()).getAsString();
                require(ids.add(id), "Повторный первичный ключ.");
            }
        }
    }
    public void validate(Warehouse data) {
        validateDocument(gson.toJsonTree(data).getAsJsonObject());
        Set<List<String>> pairs = new HashSet<>();
        for (Delivery d : data.getDeliveries()) {
            data.findClient(d.getClientId());
            require(Set.of("ожидается", "поступила", "проверена", "требует согласования", "принята", "размещена").contains(d.getStatus()), "Неизвестный статус поставки.");
            require(d.getStatus().equals("ожидается") == (d.getActualDate() == null), "Статус не согласован с датой.");
            require(data.getDeliveryItems().stream().anyMatch(i -> i.getDeliveryId().equals(d.getDeliveryId())), "Поставка должна иметь позиции.");
        }
        for (Product p : data.getProducts()) checkConditions(p.getStorageConditions());
        for (DeliveryItem i : data.getDeliveryItems()) {
            Delivery delivery = data.findDelivery(i.getDeliveryId()); data.findProduct(i.getProductId());
            require(pairs.add(List.of(i.getDeliveryId(), i.getProductId())), "Повторная позиция товара.");
            require((i.getActualQuantity() == null) == (delivery.getActualDate() == null), "Дата и факт заполняются вместе.");
        }
        for (Batch b : data.getBatches()) {
            DeliveryItem i = data.findDeliveryItem(b.getDeliveryItemId());
            BigInteger accepted = sum(data.getBatches(), x -> x.getDeliveryItemId().equals(i.getDeliveryItemId()), Batch::getQuantity);
            require(i.getActualQuantity() != null && i.getActualQuantity().signum() > 0 && accepted.compareTo(i.getActualQuantity()) <= 0, "Принято больше факта.");
            require(sum(data.getPlacements(), p -> p.getBatchId().equals(b.getBatchId()), Placement::getQuantity).compareTo(b.getQuantity()) <= 0, "Размещено больше партии.");
        }
        for (StorageCell cell : data.getStorageCells()) {
            checkConditions(cell.getStorageConditions());
            require(Set.of("доступна", "заблокирована").contains(cell.getStatus()), "Неизвестный статус ячейки.");
            BigInteger occupied = sum(data.getPlacements(), p -> p.getCellId().equals(cell.getCellId()), Placement::getQuantity);
            require(cell.getAvailableCapacity().equals(cell.getCapacity().subtract(occupied)), "Производная вместимость не согласована.");
        }
        for (Placement p : data.getPlacements()) {
            Batch b = data.findBatch(p.getBatchId()); DeliveryItem i = data.findDeliveryItem(b.getDeliveryItemId());
            Delivery d = data.findDelivery(i.getDeliveryId()); Product product = data.findProduct(i.getProductId()); StorageCell cell = data.findStorageCell(p.getCellId());
            require(d.getActualDate() != null && !p.getPlacementDate().isBefore(d.getActualDate()), "Размещение раньше поступления.");
            require(product.getStorageConditions().equals(cell.getStorageConditions()), "Неверный режим размещения.");
            require(product.getAllowedZone() == null || product.getAllowedZone().equals(cell.getZone()), "Неверная зона размещения.");
        }
        for (Discrepancy d : data.getDiscrepancies()) d.validateAgainst(data.findDeliveryItem(d.getDeliveryItemId()));
        for (Delivery d : data.getDeliveries()) validateState(data, d);
    }
    private void validateState(Warehouse data, Delivery delivery) {
        Set<String> itemIds = new HashSet<>();
        for (DeliveryItem i : data.getDeliveryItems()) if (i.getDeliveryId().equals(delivery.getDeliveryId())) itemIds.add(i.getDeliveryItemId());
        List<Batch> batches = data.getBatches().stream().filter(b -> itemIds.contains(b.getDeliveryItemId())).toList();
        List<Discrepancy> deviations = data.getDiscrepancies().stream().filter(d -> itemIds.contains(d.getDeliveryItemId())).toList();
        String status = delivery.getStatus(); boolean accepted = Set.of("принята", "размещена").contains(status);
        require(accepted == !batches.isEmpty(), "Партии не согласованы со статусом.");
        if (Set.of("ожидается", "поступила", "проверена").contains(status)) require(deviations.isEmpty(), "Расхождения не согласованы со статусом.");
        if (status.equals("требует согласования")) require(!deviations.isEmpty(), "Нет расхождений для согласования.");
        if (Set.of("проверена", "требует согласования", "принята", "размещена").contains(status)) {
            for (DeliveryItem i : data.getDeliveryItems()) if (itemIds.contains(i.getDeliveryItemId())) {
                BigInteger difference = i.getActualQuantity().subtract(i.getExpectedQuantity());
                long count = deviations.stream().filter(v -> v.getDeliveryItemId().equals(i.getDeliveryItemId()) && !v.getDiscrepancyType().equals("качество")).count();
                require((difference.signum() != 0) == (count > 0) && count <= 1, "Расхождения зарегистрированы не полностью.");
                if (status.equals("проверена")) require(difference.signum() == 0, "Проверенная поставка имеет отклонение.");
            }
        }
        if (accepted) {
            boolean complete = batches.stream().allMatch(b -> sum(data.getPlacements(), p -> p.getBatchId().equals(b.getBatchId()), Placement::getQuantity).equals(b.getQuantity()));
            require(status.equals("размещена") == complete, "Статус не согласован с размещениями.");
        }
    }
    public LocalDate parseDate(String value) {
        require(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), "Дата должна иметь формат ГГГГ-ММ-ДД.");
        LocalDate result = LocalDate.parse(value);
        require(result.getYear() >= 1 && result.toString().equals(value), "Неверная дата.");
        return result;
    }
    public void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
    private void checkConditions(String value) { require(Set.of("сухое", "охлаждение", "заморозка").contains(value), "Неизвестные условия хранения."); }
    private <T> BigInteger sum(List<T> list, java.util.function.Predicate<T> condition, Function<T, BigInteger> quantity) {
        BigInteger total = BigInteger.ZERO;
        for (T value : list) if (condition.test(value)) total = total.add(quantity.apply(value));
        return total;
    }
}
