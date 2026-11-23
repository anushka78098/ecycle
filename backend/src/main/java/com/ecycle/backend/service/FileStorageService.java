package com.ecycle.backend.service;

import com.ecycle.backend.config.AppProps;
import com.ecycle.backend.exception.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Secure uploads: allow-listed types verified by magic bytes, size caps, random server-side names, traversal-safe reads, SHA-256 fingerprints. */
@Service
public class FileStorageService {
    public record Stored(String path, String originalName, String contentType, String sha256) {}
    public static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    public static final Set<String> EVIDENCE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private final Path root;

    public FileStorageService(AppProps p) throws IOException {
        this.root = Paths.get(p.getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public Stored store(MultipartFile f, String dir, Set<String> allowed, long maxBytes) {
        if (f == null || f.isEmpty()) throw ApiException.badRequest("No file uploaded");
        if (f.getSize() > maxBytes) throw ApiException.badRequest("File exceeds the " + (maxBytes / 1024 / 1024) + " MB limit");
        String claimed = f.getContentType() == null ? "" : f.getContentType().toLowerCase(Locale.ROOT);
        if (!allowed.contains(claimed)) throw ApiException.badRequest("File type not allowed: " + claimed);
        try (InputStream in = f.getInputStream()) {
            byte[] bytes = in.readNBytes((int) maxBytes + 1);
            if (bytes.length > maxBytes) throw ApiException.badRequest("File too large");
            if (!magicMatches(bytes, claimed)) throw ApiException.badRequest("File content does not match its declared type");
            String ext = switch (claimed) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".pdf"; };
            Path folder = root.resolve(dir).normalize();
            if (!folder.startsWith(root)) throw ApiException.badRequest("Invalid storage directory");
            Files.createDirectories(folder);
            Path target = folder.resolve(UUID.randomUUID() + ext);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            String original = f.getOriginalFilename() == null ? "upload" : Paths.get(f.getOriginalFilename()).getFileName().toString();
            return new Stored(root.relativize(target).toString().replace('\\', '/'), original.length() > 190 ? original.substring(0, 190) : original, claimed, sha);
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not store file", e);
        }
    }

    /** Server-generated content (demo images). Same layout and naming rules as uploads, no multipart involved. */
    public Stored storeBytes(byte[] bytes, String dir, String contentType, String originalName) {
        try {
            String ext = switch (contentType) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".pdf"; };
            Path folder = root.resolve(dir).normalize();
            if (!folder.startsWith(root)) throw ApiException.badRequest("Invalid storage directory");
            Files.createDirectories(folder);
            Path target = folder.resolve(UUID.randomUUID() + ext);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new Stored(root.relativize(target).toString().replace('\\', '/'), originalName, contentType, sha);
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not store file", e);
        }
    }

    public Resource load(String relative) {
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p)) throw ApiException.notFound("File not found");
        return new FileSystemResource(p);
    }

    private static boolean magicMatches(byte[] b, String type) {
        if (b.length < 12) return false;
        return switch (type) {
            case "image/jpeg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "image/png" -> (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "image/webp" -> b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            case "application/pdf" -> b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
            default -> false;
        };
    }
}
