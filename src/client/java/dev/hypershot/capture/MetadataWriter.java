package dev.hypershot.capture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;
import dev.hypershot.util.AtomicJson;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;

public final class MetadataWriter {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(OffsetDateTime.class, (JsonSerializer<OffsetDateTime>) (value, type, context) -> context.serialize(value.toString()))
            .create();

    public void write(Path sidecar, CaptureMetadata metadata) throws IOException {
        AtomicJson.write(sidecar, GSON.toJson(metadata));
    }
}
