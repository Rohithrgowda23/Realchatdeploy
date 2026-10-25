package com.realchat;

import com.realchat.service.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageTest {
    @TempDir Path dir;

    @Test void storesUnderGeneratedUuidAndLoadsItBack() throws Exception {
        FileStorage s = new FileStorage(dir.toString());
        String name = s.store(new MockMultipartFile("file", "../../evil.png", "image/png", "data".getBytes()));
        assertTrue(name.matches("[0-9a-f-]{36}"));
        assertTrue(Files.exists(dir.resolve(name)));
        assertEquals(4, s.load(name).contentLength());
    }
    @Test void pathTraversalNamesAreRefused() {
        FileStorage s = new FileStorage(dir.toString());
        assertThrows(IllegalArgumentException.class, () -> s.load("../secret"));
        assertThrows(IllegalArgumentException.class, () -> s.load("..\\secret"));
    }
    @Test void deleteRemovesTheFile() throws Exception {
        FileStorage s = new FileStorage(dir.toString());
        String name = s.store(new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes()));
        s.deleteQuietly(name);
        assertFalse(Files.exists(dir.resolve(name)));
    }
}