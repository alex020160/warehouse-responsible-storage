package ru.mirea.warehouse.infrastructure;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import ru.mirea.warehouse.domain.*;

public final class JsonWarehouseRepository {
    private final Gson gson;
    private final IntegrityValidator validator;
    public JsonWarehouseRepository() {
        GsonBuilder builder = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .serializeNulls().setPrettyPrinting().disableHtmlEscaping().setStrictness(Strictness.STRICT);
        builder.registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>) (date, type, context) -> new JsonPrimitive(date.toString()));
        builder.registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) (value, type, context) -> LocalDate.parse(value.getAsString()));
        builder.registerTypeAdapter(Discrepancy.class, (JsonDeserializer<Discrepancy>) (value, type, context) -> {
            JsonObject object = value.getAsJsonObject();
            if ("качество".equals(object.get("discrepancy_type").getAsString())) return context.deserialize(value, QualityDiscrepancy.class);
            return new Discrepancy(object.get("discrepancy_id").getAsString(), object.get("delivery_item_id").getAsString(),
                    object.get("discrepancy_type").getAsString(), object.get("quantity_difference").getAsBigInteger(), object.get("description").getAsString());
        });
        gson = builder.create(); validator = new IntegrityValidator(gson);
    }
    public IntegrityValidator getValidator() { return validator; }
    public Gson getGson() { return gson; }
    public Warehouse decode(String text) {
        if (text.startsWith("\uFEFF")) throw new IllegalArgumentException("JSON должен быть UTF-8 без BOM.");
        // JsonObject keeps only the last value: check names before building the tree.
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            Deque<Set<String>> objectFields = new ArrayDeque<>();
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                switch (reader.peek()) {
                    case BEGIN_OBJECT -> { reader.beginObject(); objectFields.push(new HashSet<>()); }
                    case END_OBJECT -> { reader.endObject(); objectFields.pop(); }
                    case BEGIN_ARRAY -> reader.beginArray();
                    case END_ARRAY -> reader.endArray();
                    case NAME -> {
                        String name = reader.nextName();
                        if (!objectFields.peek().add(name)) {
                            throw new IllegalArgumentException("Повторяющееся поле JSON: " + name + " (" + reader.getPath() + ")");
                        }
                    }
                    default -> reader.skipValue();
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Неверный JSON.", ex);
        }
        JsonElement parsed = gson.fromJson(text, JsonElement.class);
        if (parsed == null || !parsed.isJsonObject()) throw new IllegalArgumentException("Ожидается объект JSON.");
        validator.validateDocument(parsed.getAsJsonObject());
        Warehouse data = gson.fromJson(parsed, Warehouse.class); validator.validate(data); return data;
    }
    public Warehouse load(Path path) throws IOException { return decode(Files.readString(path, StandardCharsets.UTF_8)); }
    public Warehouse copy(Warehouse data) { return decode(gson.toJson(data)); }
    public String encode(Object data) { return gson.toJson(data); }
    public void save(Warehouse data, Path path) throws IOException {
        validator.validate(data);
        Path target = path.toAbsolutePath(); Path temporary = Files.createTempFile(target.getParent(), "warehouse-", ".tmp");
        try {
            byte[] bytes = (gson.toJson(data) + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileChannel stream = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) stream.write(buffer);
                stream.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
