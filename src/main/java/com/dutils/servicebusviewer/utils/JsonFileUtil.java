package com.dutils.servicebusviewer.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class JsonFileUtil {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static void writeToFile(File file, Object obj) throws IOException {
        Path path = file.toPath();
        Files.createDirectories(path.getParent());

        Path tmp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        byte[] jsonBytes = mapper.writeValueAsString(obj).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(jsonBytes);
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true); // ensure data is physically written
        }
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public record JsonReadResult<T>(T object, String rawData) {}

    public static <T> JsonReadResult<T> readFromFile(File file, Class<T> type) {
        if (file == null || !file.exists()) return new JsonReadResult<>(null, null);

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            T obj = mapper.readValue(bytes, type);
            return new JsonReadResult<>(obj, null);
        } catch (Exception e) {
            try {
                String raw = Files.readString(file.toPath());
                return new JsonReadResult<>(null, raw);
            } catch (IOException ioEx) {
                return new JsonReadResult<>(null, null);
            }
        }
    }
    public static void writeToFile( String file,Object obj) throws IOException {
        writeToFile( new File(file),obj);
    }

}