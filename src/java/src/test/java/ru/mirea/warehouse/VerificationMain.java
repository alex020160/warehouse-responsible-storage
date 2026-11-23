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
    private void selfTest(Path seed) throws Exception {
        Warehouse data=repository.load(seed);
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
        System.out.println("PASS: "+requests.size()+" Python/Java reference cases, JSON round trip, rejected save preserves file");
    }
    public static void main(String[] args) throws Exception {
        VerificationMain check=new VerificationMain();
        if(args.length>0&&args[0].equals("--self-test")) { check.selfTest(Path.of(args.length>1?args[1]:"data/warehouse.json"));return; }
        JsonArray requests=JsonParser.parseString(Files.readString(Path.of(args[0]),StandardCharsets.UTF_8)).getAsJsonArray();JsonArray results=new JsonArray();
        for(JsonElement request:requests) results.add(check.execute(request.getAsJsonObject()));
        Files.writeString(Path.of(args[1]),check.gson.toJson(results),StandardCharsets.UTF_8);
    }
}
