package ru.mirea.warehouse;

import com.google.gson.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import ru.mirea.warehouse.application.*;
import ru.mirea.warehouse.domain.*;
import ru.mirea.warehouse.infrastructure.*;

/** Небольшой исполняемый проверочный стенд; не является сущностью модели. */
public final class VerificationMain {
    private final JsonWarehouseRepository repository = new JsonWarehouseRepository();
    private final Gson gson = repository.getGson();
    private DeliveryItem item(JsonElement value) { return gson.fromJson(value, DeliveryItem.class); }
    private List<DeliveryItem> items(JsonArray array) {
        List<DeliveryItem> result = new ArrayList<>();
        for (JsonElement value : array) result.add(item(value));
        return result;
    }
    private List<Map<String,Object>> deviations(JsonArray array) {
        List<Map<String,Object>> result = new ArrayList<>();
        for (JsonElement value : array) {
            JsonObject object=value.getAsJsonObject(); Map<String,Object> entry=new LinkedHashMap<>();
            for (var field:object.entrySet()) entry.put(field.getKey(), field.getKey().equals("quantity_difference") ? field.getValue().getAsBigInteger() : field.getValue().getAsString());
            result.add(entry);
        }
        return result;
    }
    private Object operation(ReceivingService service, JsonObject op) {
        String name=op.get("op").getAsString();
        return switch(name) {
            case "expected" -> service.registerExpectedDelivery(op.get("client_id").getAsString(), LocalDate.parse(op.get("date").getAsString()), items(op.getAsJsonArray("items")));
            case "actual" -> service.recordActualReceipt(op.get("id").getAsString(), items(op.getAsJsonArray("items")), LocalDate.parse(op.get("date").getAsString()));
            case "compare_receipt" -> service.receiptDiscrepancies(op.get("id").getAsString());
            case "discrepancies" -> service.registerDiscrepancies(op.get("id").getAsString(), deviations(op.getAsJsonArray("items")));
            case "accept" -> {
                Map<String,Batch> details=new LinkedHashMap<>();
                for(var entry:op.getAsJsonObject("details").entrySet()) details.put(entry.getKey(),gson.fromJson(entry.getValue(),Batch.class));
                yield service.acceptDelivery(op.get("id").getAsString(),details,op.get("approved").getAsBoolean());
            }
            case "select_batch" -> service.selectForBatch(op.get("id").getAsString()).orElse(null);
            case "place" -> service.registerPlacement(gson.fromJson(op.get("placement"),Placement.class),op.has("zone")&&!op.get("zone").isJsonNull()?op.get("zone").getAsString():null);
            default -> throw new IllegalArgumentException("Неизвестная операция: "+name);
        };
    }
    public JsonElement execute(JsonObject request) throws Exception {
        String mode=request.get("mode").getAsString();
        if(mode.equals("compare")) return gson.toJsonTree(new DeliveryComparator().compareDeliveryItems(items(request.getAsJsonArray("expected")),items(request.getAsJsonArray("actual"))));
        if(mode.equals("select")) {
            List<StorageCell> cells=new ArrayList<>();
            for(JsonElement value:request.getAsJsonArray("cells")) cells.add(gson.fromJson(value,StorageCell.class));
            return gson.toJsonTree(new StorageSelector().selectStorageCell(gson.fromJson(request.get("product"),Product.class),request.get("quantity").getAsBigInteger(),cells).orElse(null));
        }
        Warehouse data;
        try { data=repository.decode(gson.toJson(request.get("data"))); }
        catch(RuntimeException ex) { JsonObject failure=new JsonObject();failure.addProperty("valid",false);return failure; }
        if(mode.equals("validate")) { JsonObject ok=new JsonObject();ok.addProperty("valid",true);return ok; }
        if(mode.equals("io")) {
            Path directory=Files.createTempDirectory("warehouse-check-");Path file=directory.resolve("warehouse.json");
            try { repository.save(data,file);return gson.toJsonTree(repository.load(file)); }
            finally { Files.deleteIfExists(file);Files.deleteIfExists(directory); }
        }
        JsonArray results=new JsonArray();
        for(JsonElement value:request.getAsJsonArray("operations")) {
            Warehouse candidate=repository.copy(data);JsonObject result=new JsonObject();
            try {
                Object output=operation(new ReceivingService(candidate,repository.getValidator()),value.getAsJsonObject());
                repository.getValidator().validate(candidate);result.addProperty("ok",true);result.add("result",gson.toJsonTree(output));data=candidate;
            } catch(RuntimeException ex) { result.addProperty("ok",false); }
            results.add(result);
        }
        JsonObject response=new JsonObject();response.add("results",results);response.add("state",gson.toJsonTree(data));return response;
    }
    private void assertRejectedUnchanged(Warehouse data, Runnable operation) {
        String before=repository.encode(data); boolean rejected=false;
        try { operation.run(); } catch (IllegalArgumentException ex) { rejected=true; }
        if (!rejected || !before.equals(repository.encode(data))) throw new AssertionError("Rejected service call changed Warehouse");
        repository.getValidator().validate(data);
    }
    private void selfTest(Path seed) throws Exception {
        Warehouse data=repository.load(seed);
        // Review #1: preserve duplicate fields in raw strings, not in JsonObject.
        String raw=repository.encode(data);
        String capacity="\"capacity\": 20";
        if (!raw.contains(capacity)) throw new AssertionError("Missing capacity fixture");
        for (String invalid : List.of(
                raw.replace(capacity,"\"capacity\": 999, \"capacity\": 20"),
                raw.replace(capacity,"\"capacity\": 20, \"capacity\": 20"),
                raw.replace(capacity,"\"cap\\u0061city\": 999, \"capacity\": 20"),
                "{\"clients\": []," + raw.substring(1), raw + " {}", raw.substring(0,raw.length()-1))) {
            boolean rejected=false;
            try { repository.decode(invalid); } catch (RuntimeException ex) { rejected=true; }
            if (!rejected) throw new AssertionError("Invalid or duplicate JSON was accepted");
        }
        if (!repository.encode(repository.decode(raw)).equals(raw)) throw new AssertionError("Valid JSON changed");
        // Review #2: direct calls must reject unsupported dates before any mutation.
        for (LocalDate invalidDate : Arrays.asList(LocalDate.of(0,1,1), LocalDate.of(-1,1,1), LocalDate.of(10000,1,1), null)) {
            Warehouse expectedData=repository.copy(data);
            ReceivingService expectedService=new ReceivingService(expectedData,repository.getValidator());
            assertRejectedUnchanged(expectedData, () -> expectedService.registerExpectedDelivery("C001",invalidDate,
                    List.of(new DeliveryItem(null,null,"P001",BigInteger.ONE,null))));
            Warehouse received=repository.copy(data);
            ReceivingService service=new ReceivingService(received,repository.getValidator());
            List<DeliveryItem> actualItems=List.of(new DeliveryItem(null,null,"P001",null,BigInteger.TEN));
            assertRejectedUnchanged(received, () -> service.recordActualReceipt("D001",actualItems,invalidDate));
            service.recordActualReceipt("D001",actualItems,LocalDate.of(2026,11,22));
            service.registerDiscrepancies("D001",List.of());
            if (invalidDate != null) {
                assertRejectedUnchanged(received, () -> service.acceptDelivery("D001",
                        Map.of("I001",new Batch(null,null,"REVIEW",invalidDate,BigInteger.TEN)),true));
            }
            service.acceptDelivery("D001",Map.of("I001",new Batch(null,null,"REVIEW",null,BigInteger.TEN)),true);
            assertRejectedUnchanged(received, () -> service.registerPlacement(
                    new Placement("PL002","B002","S001",BigInteger.TEN,invalidDate),null));
        }
        for (LocalDate validDate : List.of(LocalDate.of(1,1,1),LocalDate.of(9999,12,31))) {
            Warehouse candidate=repository.copy(data);
            new ReceivingService(candidate,repository.getValidator()).registerExpectedDelivery("C001",validDate,
                    List.of(new DeliveryItem(null,null,"P001",BigInteger.ONE,null)));
            repository.getValidator().validate(candidate);
        }
        DeliveryItem expected=new DeliveryItem(null,null,"P001",BigInteger.TEN,null);
        DeliveryItem actual=new DeliveryItem(null,null,"P001",null,BigInteger.valueOf(8));
        var result=new DeliveryComparator().compareDeliveryItems(List.of(expected),List.of(actual));
        if(!result.getFirst().get("quantity_difference").equals(BigInteger.valueOf(-2))) throw new AssertionError("Недостача");
        if(!new DeliveryComparator().compareDeliveryItems(List.of(),List.of()).isEmpty()) throw new AssertionError("Пустые списки");
        if(new StorageSelector().selectStorageCell(data.findProduct("P001"),BigInteger.TEN,data.getStorageCells()).isEmpty()) throw new AssertionError("Подбор");
        JsonObject io=new JsonObject();io.addProperty("mode","io");io.add("data",gson.toJsonTree(data));
        if(!execute(io).equals(gson.toJsonTree(data))) throw new AssertionError("JSON round trip");
        Path inputs=Path.of("verification/parity-input.json");
        JsonArray requests=JsonParser.parseString(Files.readString(inputs,StandardCharsets.UTF_8)).getAsJsonArray();
        JsonArray expectedResults=JsonParser.parseString(Files.readString(Path.of("verification/parity-python.json"),StandardCharsets.UTF_8)).getAsJsonArray();
        for(int i=0;i<requests.size();i++) if(!execute(requests.get(i).getAsJsonObject()).equals(expectedResults.get(i))) throw new AssertionError("Parity case "+i);
        Path directory=Files.createTempDirectory("warehouse-preserve-");Path file=directory.resolve("warehouse.json");
        try {
            repository.save(data,file);String before=Files.readString(file,StandardCharsets.UTF_8);
            JsonObject invalid=gson.toJsonTree(data).getAsJsonObject();
            invalid.getAsJsonArray("storage_cells").get(0).getAsJsonObject().addProperty("available_capacity",999);
            Warehouse broken=gson.fromJson(invalid,Warehouse.class);boolean rejected=false;
            try { repository.save(broken,file); } catch(IllegalArgumentException ex) { rejected=true; }
            if(!rejected||!before.equals(Files.readString(file,StandardCharsets.UTF_8))) throw new AssertionError("Invalid save changed file");
            try(var files=Files.list(directory)) { if(files.count()!=1) throw new AssertionError("Temporary files leaked"); }
        } finally { Files.deleteIfExists(file);Files.deleteIfExists(directory); }
        System.out.println("PASS: "+requests.size()+" Python/Java reference cases, JSON round trip, 6 raw JSON rejection cases, 15 rejected date calls preserve state, 2 valid date boundaries, rejected save preserves file");
    }
    public static void main(String[] args) throws Exception {
        VerificationMain check=new VerificationMain();
        if(args.length>0&&args[0].equals("--self-test")) { check.selfTest(Path.of(args.length>1?args[1]:"data/warehouse.json"));return; }
        JsonArray requests=JsonParser.parseString(Files.readString(Path.of(args[0]),StandardCharsets.UTF_8)).getAsJsonArray();JsonArray results=new JsonArray();
        for(JsonElement request:requests) results.add(check.execute(request.getAsJsonObject()));
        Files.writeString(Path.of(args[1]),check.gson.toJson(results),StandardCharsets.UTF_8);
    }
}
