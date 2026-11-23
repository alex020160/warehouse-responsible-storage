package ru.mirea.warehouse.cli;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import ru.mirea.warehouse.domain.*;
import ru.mirea.warehouse.application.*;
import ru.mirea.warehouse.infrastructure.*;

public final class ConsoleApp {
    private final JsonWarehouseRepository repository;
    private final BufferedReader input;
    private final PrintStream output;
    public ConsoleApp(JsonWarehouseRepository repository) {
        this.repository = repository;
        output = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }
    public static void main(String[] args) {
        int code = new ConsoleApp(new JsonWarehouseRepository()).run(Path.of(args.length == 0 ? "data/warehouse.json" : args[0]));
        if (code != 0) System.exit(code);
    }
    public int run(Path path) {
        Warehouse data;
        try { data = repository.load(path); }
        catch (IOException | RuntimeException error) {
            output.println("Не удалось загрузить данные: " + error.getMessage()); return 1;
        }
        while (true) {
            try {
                output.println("\n1. Зарегистрировать ожидаемую поставку\n2. Сопоставить фактическое поступление с ожидаемой поставкой\n3. Зарегистрировать расхождения\n4. Подобрать место хранения\n5. Зарегистрировать размещение товара\n6. Просмотреть данные\n0. Выход");
                String choice = ask("Выбор: ");
                if (choice.equals("0")) return 0;
                if (choice.equals("6")) { show(data); continue; }
                if (choice.equals("4")) {
                    ReceivingService service = new ReceivingService(data, repository.getValidator());
                    String id = chooseBatch(data); Optional<StorageCell> cell = service.selectForBatch(id);
                    if (cell.isPresent()) show(cell.get()); else output.println("Подходящей ячейки нет.");
                    continue;
                }
                if (!Set.of("1", "2", "3", "5").contains(choice)) { output.println("Введите номер пункта от 0 до 6."); continue; }
                Warehouse candidate = repository.copy(data);
                ReceivingService service = new ReceivingService(candidate, repository.getValidator());
                Object result = switch (choice) {
                    case "1" -> inputExpected(candidate, service);
                    case "2" -> inputActual(service);
                    case "3" -> inputDiscrepancies(candidate, service);
                    default -> inputPlacement(candidate, service);
                };
                if (result != null) {
                    if (confirm("Сохранить изменения в JSON?")) {
                        repository.save(candidate, path); data = candidate;
                        output.println("Изменения сохранены."); show(result);
                    } else output.println("Изменения отменены.");
                }
            } catch (EOFException error) { output.println("Работа завершена. Неподтверждённые изменения не сохраняются."); return 0; }
            catch (IOException | RuntimeException error) { output.println("Ошибка ввода или сохранения: " + error.getMessage() + ". Повторите операцию."); }
        }
    }
    private Object inputExpected(Warehouse data, ReceivingService service) throws IOException {
        show(data.getClients()); String client = ask("Клиент client_id: "); LocalDate date = readDate("Ожидаемая дата");
        show(data.getProducts()); return service.registerExpectedDelivery(client, date, readItems(false));
    }
    private Object inputActual(ReceivingService service) throws IOException {
        String id = ask("Поставка delivery_id: ");
        if (!confirm("Ожидаемая поставка и сопроводительные документы найдены?")) {
            output.println("Запросите уточнения у клиента. Фактическое поступление не сохранено."); return null;
        }
        LocalDate date = readDate("Дата поступления"); List<DeliveryItem> items = readItems(true);
        List<String> changed = service.recordActualReceipt(id, items, date);
        List<Map<String, Object>> deviations = service.receiptDiscrepancies(id);
        output.println("Результат сопоставления (пустой список — расхождений нет):"); show(deviations);
        Map<String, Object> result = new LinkedHashMap<>(); result.put("delivery_id", id);
        result.put("updated_item_ids", changed); result.put("discrepancies", deviations); return result;
    }
    private Object inputDiscrepancies(Warehouse data, ReceivingService service) throws IOException {
        String id = ask("Поставка delivery_id: "); List<Map<String, Object>> deviations = service.receiptDiscrepancies(id);
        List<DeliveryItem> items = service.deliveryItems(id); Set<String> ids = new HashSet<>();
        for (DeliveryItem item : items) ids.add(item.getDeliveryItemId());
        for (Discrepancy entry : data.getDiscrepancies()) if (ids.contains(entry.getDeliveryItemId()) && entry.getDiscrepancyType().equals("качество")) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("product_id", data.findDeliveryItem(entry.getDeliveryItemId()).getProductId());
            value.put("discrepancy_type", "качество"); value.put("quantity_difference", BigInteger.ZERO); value.put("description", entry.getDescription()); deviations.add(value);
        }
        while (confirm("Добавить результат качественной проверки?")) {
            Map<String, Object> value = new LinkedHashMap<>(); value.put("product_id", ask("Товар product_id: "));
            value.put("discrepancy_type", "качество"); value.put("quantity_difference", BigInteger.ZERO);
            value.put("description", ask("Описание отклонения: ")); deviations.add(value);
        }
        List<Discrepancy> result = service.registerDiscrepancies(id, deviations); show(result);
        if (!confirm("Подтверждены принимаемые количества? Расхождения согласованы с клиентом, критические отклонения урегулированы")) {
            output.println("Расхождения сохранятся; партии не созданы. Продолжить можно через пункт 3."); return result;
        }
        Map<String, Batch> details = new LinkedHashMap<>();
        for (DeliveryItem item : items) if (item.getActualQuantity().signum() > 0) {
            output.println("Позиция " + item.getDeliveryItemId() + ", товар " + item.getProductId() + ", факт " + item.getActualQuantity());
            BigInteger quantity = new BigInteger(ask("Принятое количество (0 — не принимать позицию): "));
            if (quantity.signum() == 0) continue;
            String number = ask("Номер партии: "); String expiry = ask("Срок годности ГГГГ-ММ-ДД (пусто — нет): ");
            details.put(item.getDeliveryItemId(), new Batch(null, item.getDeliveryItemId(), number,
                    expiry.isEmpty() ? null : repository.getValidator().parseDate(expiry), quantity));
        }
        return service.acceptDelivery(id, details, true);
    }
    private String chooseBatch(Warehouse data) throws IOException { show(data.getBatches()); return ask("Партия batch_id: "); }
    private Object inputPlacement(Warehouse data, ReceivingService service) throws IOException {
        String id = chooseBatch(data); Optional<StorageCell> cell = service.selectForBatch(id);
        if (cell.isEmpty()) { output.println("Подходящей ячейки нет. Размещение не зарегистрировано."); return null; }
        show(cell.get()); if (!confirm("Товар физически размещён в выбранной ячейке?")) return null;
        String placementId = service.newId(data.getPlacements(), Placement::getPlacementId, "PL");
        return service.registerPlacement(new Placement(placementId, id, cell.get().getCellId(), service.batchRemaining(id), readDate("Дата размещения")), null);
    }
    private List<DeliveryItem> readItems(boolean actual) throws IOException {
        output.println("Формат: P001:10,P002:5. Пустая строка — нет фактического товара.");
        String text = ask("Позиции: "); List<DeliveryItem> items = new ArrayList<>();
        if (!text.isEmpty()) for (String part : text.split(",", -1)) {
            String[] pair = part.split(":", -1);
            if (pair.length != 2) throw new IllegalArgumentException("Нужен формат товар:количество.");
            BigInteger quantity = new BigInteger(pair[1].strip());
            items.add(new DeliveryItem(null, null, pair[0].strip(), actual ? null : quantity, actual ? quantity : null));
        }
        return items;
    }
    private String ask(String prompt) throws IOException {
        output.print(prompt); String line = input.readLine(); if (line == null) throw new EOFException(); return line.strip();
    }
    private boolean confirm(String prompt) throws IOException {
        String value = ask(prompt + " [да/нет]: ").toLowerCase(Locale.ROOT);
        if (!Set.of("да", "нет").contains(value)) throw new IllegalArgumentException("Введите «да» или «нет»."); return value.equals("да");
    }
    private LocalDate readDate(String prompt) throws IOException {
        String value = ask(prompt + " (ГГГГ-ММ-ДД, пусто — сегодня): ");
        return value.isEmpty() ? LocalDate.now() : repository.getValidator().parseDate(value);
    }
    private void show(Object value) { output.println(repository.encode(value)); }
}
