package com.realchat.service;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Decides what an upload really is from its first bytes. The browser-supplied MIME type is never used. Only an
 * allow-list is accepted; HTML, SVG, scripts and executables are refused, so no active content is ever stored or served.
 */
public final class FileTypeDetector {
    private FileTypeDetector() {}

    public record Detected(String kind, String mime) {}

    private static final Set<String> BLOCKED_EXT = Set.of("html", "htm", "xhtml", "svg", "js", "mjs", "exe", "bat", "cmd", "com",
            "ps1", "sh", "jar", "msi", "dll", "scr", "vbs", "php", "hta", "apk");
    private static final Set<String> TEXT_EXT = Set.of("txt", "csv", "md", "log", "json");

    /** Display-only file name: no path parts, no control or shell-special characters, bounded length. */
    public static String safeName(String original) {
        String n = original == null ? "file" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").strip();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) n = "file";
        return n.length() > 150 ? n.substring(n.length() - 150) : n;
    }

    public static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 || i == name.length() - 1 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    public static Optional<Detected> detect(byte[] h, String name, boolean voice) {
        if (h == null || h.length < 4) return Optional.empty();
        String ext = extension(name);
        if (BLOCKED_EXT.contains(ext)) return Optional.empty();

        if (starts(h, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return d(voice, "IMAGE", "image/png");
        if (starts(h, 0xFF, 0xD8, 0xFF)) return d(voice, "IMAGE", "image/jpeg");
        if (ascii(h, 0, "GIF87a") || ascii(h, 0, "GIF89a")) return d(voice, "IMAGE", "image/gif");
        if (ascii(h, 0, "RIFF") && ascii(h, 8, "WEBP")) return d(voice, "IMAGE", "image/webp");
        if (ascii(h, 0, "RIFF") && ascii(h, 8, "WAVE")) return Optional.of(new Detected("AUDIO", "audio/wav"));
        if (ascii(h, 0, "%PDF-")) return d(voice, "DOCUMENT", "application/pdf");
        if (ascii(h, 4, "ftyp")) {
            boolean m4a = ascii(h, 8, "M4A ");
            if (voice || m4a) return Optional.of(new Detected("AUDIO", "audio/mp4"));
            return Optional.of(new Detected("VIDEO", ascii(h, 8, "qt  ") ? "video/quicktime" : "video/mp4"));
        }
        if (starts(h, 0x1A, 0x45, 0xDF, 0xA3)) return Optional.of(voice ? new Detected("AUDIO", "audio/webm") : new Detected("VIDEO", "video/webm"));
        if (ascii(h, 0, "OggS")) return Optional.of(new Detected("AUDIO", "audio/ogg"));
        if (ascii(h, 0, "ID3") || (h[0] == (byte) 0xFF && (h[1] & 0xE0) == 0xE0)) return Optional.of(new Detected("AUDIO", "audio/mpeg"));
        if (starts(h, 0x50, 0x4B, 0x03, 0x04)) { // ZIP container: only office formats / plain zip, by extension
            return switch (ext) {
                case "docx" -> d(voice, "DOCUMENT", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                case "xlsx" -> d(voice, "DOCUMENT", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                case "pptx" -> d(voice, "DOCUMENT", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
                case "zip" -> d(voice, "DOCUMENT", "application/zip");
                default -> Optional.empty();
            };
        }
        if (TEXT_EXT.contains(ext) && looksLikeText(h)) return d(voice, "DOCUMENT", "text/plain");
        return Optional.empty();
    }

    private static Optional<Detected> d(boolean voice, String kind, String mime) { return voice ? Optional.empty() : Optional.of(new Detected(kind, mime)); }

    private static boolean looksLikeText(byte[] h) {
        for (byte b : h) { int v = b & 0xFF; if (v == 0 || (v < 0x09) || (v > 0x0D && v < 0x20)) return false; }
        return true;
    }
    private static boolean starts(byte[] h, int... sig) {
        if (h.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if ((h[i] & 0xFF) != sig[i]) return false;
        return true;
    }
    private static boolean ascii(byte[] h, int off, String s) {
        if (h.length < off + s.length()) return false;
        for (int i = 0; i < s.length(); i++) if (h[off + i] != (byte) s.charAt(i)) return false;
        return true;
    }
}