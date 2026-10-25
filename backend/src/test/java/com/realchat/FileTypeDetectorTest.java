package com.realchat;

import com.realchat.service.FileTypeDetector;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeDetectorTest {
    private static byte[] b(int... v) { byte[] r = new byte[32]; for (int i = 0; i < v.length; i++) r[i] = (byte) v[i]; return r; }
    private static byte[] text(String s) { byte[] r = new byte[Math.max(8, s.length())]; byte[] t = s.getBytes(StandardCharsets.US_ASCII); System.arraycopy(t, 0, r, 0, t.length); for (int i = t.length; i < r.length; i++) r[i] = ' '; return r; }

    @Test void pngDetectedByBytesNotByName() {
        var d = FileTypeDetector.detect(b(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), "photo.bin", false).orElseThrow();
        assertEquals("IMAGE", d.kind()); assertEquals("image/png", d.mime());
    }
    @Test void jpegAndPdfDetected() {
        assertEquals("image/jpeg", FileTypeDetector.detect(b(0xFF, 0xD8, 0xFF, 0xE0), "a.jpg", false).orElseThrow().mime());
        assertEquals("application/pdf", FileTypeDetector.detect(text("%PDF-1.7"), "a.pdf", false).orElseThrow().mime());
    }
    @Test void executableDisguisedAsPngRejected() {
        assertTrue(FileTypeDetector.detect(text("MZ\u0090\u0000"), "photo.png", false).isEmpty());
    }
    @Test void htmlAndSvgRejectedEvenWithHarmlessNames() {
        assertTrue(FileTypeDetector.detect(text("<html><script>alert(1)</script>"), "page.txt", false).isEmpty());
        assertTrue(FileTypeDetector.detect(text("<svg onload=alert(1)>"), "pic.svg", false).isEmpty());
        assertTrue(FileTypeDetector.detect(b(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), "x.html", false).isEmpty());
    }
    @Test void webmIsVideoNormallyButAudioForVoice() {
        assertEquals("VIDEO", FileTypeDetector.detect(b(0x1A, 0x45, 0xDF, 0xA3), "c.webm", false).orElseThrow().kind());
        assertEquals("AUDIO", FileTypeDetector.detect(b(0x1A, 0x45, 0xDF, 0xA3), "voice.webm", true).orElseThrow().kind());
    }
    @Test void voiceModeRefusesImagesAndDocuments() {
        assertTrue(FileTypeDetector.detect(b(0xFF, 0xD8, 0xFF, 0xE0), "a.jpg", true).isEmpty());
        assertTrue(FileTypeDetector.detect(text("%PDF-1.7"), "a.pdf", true).isEmpty());
    }
    @Test void zipOnlyAllowedForOfficeAndZipExtensions() {
        assertEquals("DOCUMENT", FileTypeDetector.detect(b(0x50, 0x4B, 0x03, 0x04), "report.docx", false).orElseThrow().kind());
        assertTrue(FileTypeDetector.detect(b(0x50, 0x4B, 0x03, 0x04), "payload.jar", false).isEmpty());
        assertTrue(FileTypeDetector.detect(b(0x50, 0x4B, 0x03, 0x04), "payload.bin", false).isEmpty());
    }
    @Test void plainTextNeedsATextExtensionAndNoBinaryBytes() {
        assertEquals("DOCUMENT", FileTypeDetector.detect(text("hello world"), "notes.txt", false).orElseThrow().kind());
        assertTrue(FileTypeDetector.detect(text("hello world"), "notes.dat", false).isEmpty());
        assertTrue(FileTypeDetector.detect(b('h', 'i', 0, 1, 2, 3), "notes.txt", false).isEmpty());
    }
    @Test void safeNameStripsPathsAndControlCharacters() {
        assertEquals("passwd", FileTypeDetector.safeName("../../etc/passwd"));
        assertEquals("evil.png", FileTypeDetector.safeName("C:\\temp\\evil.png"));
        assertEquals("file", FileTypeDetector.safeName(".."));
        assertEquals("file", FileTypeDetector.safeName(null));
        assertFalse(FileTypeDetector.safeName("a<b>\"c\u0000.txt").contains("<"));
    }
}