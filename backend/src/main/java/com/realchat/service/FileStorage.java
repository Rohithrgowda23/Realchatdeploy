package com.realchat.service;

import com.realchat.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Local-disk storage outside the source tree. Files are named with server-generated UUIDs only, so a client can never influence a path. */
@Component
public class FileStorage {
    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);
    private static final Pattern NAME = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private final Path base;

    public FileStorage(@Value("${app.storage.dir:./uploads}") String dir) {
        Path p = Path.of(dir).toAbsolutePath().normalize();
        try { Files.createDirectories(p); } catch (IOException e) { throw new IllegalStateException("Cannot create upload directory " + p, e); }
        this.base = p;
    }

    public String store(MultipartFile file) {
        String name = UUID.randomUUID().toString();
        try (InputStream in = file.getInputStream()) { Files.copy(in, resolve(name)); }
        catch (IOException e) { log.error("Could not store upload: {}", e.getClass().getSimpleName()); throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the file"); }
        return name;
    }

    public Resource load(String storedName) {
        Path p = resolve(storedName);
        if (!Files.isReadable(p)) throw ApiException.notFound("File not found");
        return new FileSystemResource(p);
    }

    public void deleteQuietly(String storedName) {
        try { Files.deleteIfExists(resolve(storedName)); } catch (Exception e) { log.warn("Could not delete stored file: {}", e.getClass().getSimpleName()); }
    }

    /** Names of files older than the given age (for orphan cleanup). */
    public List<String> listOlderThan(Duration age) {
        List<String> out = new ArrayList<>();
        Instant cutoff = Instant.now().minus(age);
        try (Stream<Path> s = Files.list(base)) {
            s.filter(Files::isRegularFile).forEach(p -> {
                try {
                    FileTime t = Files.getLastModifiedTime(p);
                    String n = p.getFileName().toString();
                    if (t.toInstant().isBefore(cutoff) && NAME.matcher(n).matches()) out.add(n);
                } catch (IOException ignored) { /* skip */ }
            });
        } catch (IOException e) { log.warn("Could not list upload directory"); }
        return out;
    }

    Path resolve(String name) {
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid storage name");
        Path p = base.resolve(name).normalize();
        if (!p.startsWith(base)) throw new IllegalArgumentException("Invalid storage path");
        return p;
    }
}