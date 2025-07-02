package com.brendlij.fily;

import com.brendlij.fily.security.JwtUtil;
import io.jsonwebtoken.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

@RestController
@RequestMapping("/api/files")
@CrossOrigin
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Value("${fileserver.basedir}")
    private String baseDir;

    private final JwtUtil jwtUtil;

    public FileController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    private String getUsernameFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("Kein oder ungültiger Authorization Header");
            throw new RuntimeException("Kein Token");
        }
        String token = authHeader.substring(7);
        String username = jwtUtil.validateTokenAndGetUsername(token);
        if (username == null) {
            logger.warn("Ungültiges JWT Token");
            throw new RuntimeException("Ungültiges Token");
        }
        logger.debug("Username aus JWT: {}", username);
        return username;
    }

    private File safeFile(String username, String subPath) {
        if (subPath.contains("..")) {
            logger.warn("Ungültiger Pfad mit '..': {}", subPath);
            throw new IllegalArgumentException("Pfad ungültig!");
        }
        Path fullPath = Paths.get(baseDir, username, subPath).normalize();
        if (!fullPath.startsWith(Paths.get(baseDir, username))) {
            logger.warn("Pfad nicht erlaubt: {}", fullPath);
            throw new IllegalArgumentException("Pfad ungültig!");
        }
        return fullPath.toFile();
    }

    @GetMapping
    public ResponseEntity<?> listFiles(@RequestHeader("Authorization") String auth, @RequestParam(defaultValue = "") String path) {
        try {
            String username = getUsernameFromHeader(auth);
            File folder = safeFile(username, path);
            if (!folder.exists() || !folder.isDirectory()) {
                logger.warn("Ordner nicht gefunden für Benutzer {}: {}", username, folder.getAbsolutePath());
                return ResponseEntity.status(404).body("Ordner nicht gefunden!");
            }

            File[] files = folder.listFiles();
            List<Map<String, Object>> result = new ArrayList<>();
            if (files != null) {
                for (File file : files) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("name", file.getName());
                    entry.put("isDirectory", file.isDirectory());
                    entry.put("size", file.isFile() ? file.length() : null);
                    entry.put("lastModified", file.lastModified());
                    result.add(entry);
                }
            }
            logger.debug("Liste der Dateien für Benutzer {} im Pfad {}: {} Einträge", username, path, result.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Fehler bei listFiles", e);
            return ResponseEntity.status(500).body("Interner Serverfehler");
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestHeader("Authorization") String auth,
                                        @RequestParam("file") MultipartFile file,
                                        @RequestParam(defaultValue = "") String path) {
        try {
            String username = getUsernameFromHeader(auth);
            File dir = safeFile(username, path);
            if (!dir.exists()) dir.mkdirs();
            File dest = new File(dir, file.getOriginalFilename());
            file.transferTo(dest);
            logger.info("Datei hochgeladen von Benutzer {}: {}", username, dest.getAbsolutePath());
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            logger.error("Fehler beim Datei-Upload", e);
            return ResponseEntity.status(500).body("Fehler beim Hochladen");
        }
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestHeader("Authorization") String auth,
                                                 @RequestParam String path) {
        try {
            String username = getUsernameFromHeader(auth);
            File file = safeFile(username, path);
            if (!file.exists()) {
                logger.warn("Download: Datei nicht gefunden für Benutzer {}: {}", username, path);
                return ResponseEntity.status(404).build();
            }

            Resource resource = file.isDirectory()
                    ? new UrlResource(createZipFromDirectory(file).toURI())
                    : new UrlResource(file.toURI());

            String filename = file.isDirectory() ? file.getName() + ".zip" : file.getName();
            String contentType = file.isDirectory() ? "application/zip" : "application/octet-stream";

            logger.info("Datei-Download für Benutzer {}: {}", username, filename);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(resource);
        } catch (Exception e) {
            logger.error("Fehler beim Download", e);
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/mkdir")
    public ResponseEntity<?> makeDir(@RequestHeader("Authorization") String auth,
                                     @RequestParam String path) {
        try {
            String username = getUsernameFromHeader(auth);
            File dir = safeFile(username, path);
            if (dir.exists()) {
                logger.warn("Verzeichnis existiert bereits für Benutzer {}: {}", username, path);
                return ResponseEntity.status(400).body("Ordner existiert schon!");
            }
            boolean created = dir.mkdirs();
            if (created) {
                logger.info("Verzeichnis erstellt für Benutzer {}: {}", username, path);
                return ResponseEntity.ok("Ordner erstellt: " + path);
            } else {
                logger.error("Fehler beim Erstellen des Ordners für Benutzer {}: {}", username, path);
                return ResponseEntity.status(500).body("Fehler beim Erstellen!");
            }
        } catch (Exception e) {
            logger.error("Fehler beim Erstellen des Ordners", e);
            return ResponseEntity.status(500).body("Interner Serverfehler");
        }
    }

    @DeleteMapping
    public ResponseEntity<?> deleteFileOrDir(@RequestHeader("Authorization") String auth,
                                             @RequestParam String path) {
        try {
            String username = getUsernameFromHeader(auth);
            File file = safeFile(username, path);
            if (!file.exists()) {
                logger.warn("Löschen: Datei/Ordner nicht gefunden für Benutzer {}: {}", username, path);
                return ResponseEntity.status(404).body("Nicht gefunden!");
            }
            boolean deleted = deleteDirRecursive(file);
            if (deleted) {
                logger.info("Datei/Ordner gelöscht für Benutzer {}: {}", username, path);
                return ResponseEntity.ok("Gelöscht: " + path);
            } else {
                logger.error("Fehler beim Löschen für Benutzer {}: {}", username, path);
                return ResponseEntity.status(500).body("Fehler beim Löschen!");
            }
        } catch (Exception e) {
            logger.error("Fehler beim Löschen", e);
            return ResponseEntity.status(500).body("Interner Serverfehler");
        }
    }

    @PostMapping("/rename")
    public ResponseEntity<?> rename(@RequestHeader("Authorization") String auth,
                                    @RequestParam String oldPath,
                                    @RequestParam String newName) {
        try {
            String username = getUsernameFromHeader(auth);
            File oldFile = safeFile(username, oldPath);
            if (!oldFile.exists()) {
                logger.warn("Umbenennen: Datei nicht gefunden für Benutzer {}: {}", username, oldPath);
                return ResponseEntity.status(404).body("Nicht gefunden!");
            }
            if (newName.contains("..") || newName.contains("/") || newName.contains("\\")) {
                logger.warn("Umbenennen: Ungültiger neuer Name {} für Benutzer {}", newName, username);
                return ResponseEntity.status(400).body("Ungültiger Name!");
            }
            File newFile = new File(oldFile.getParentFile(), newName);
            boolean renamed = oldFile.renameTo(newFile);
            if (renamed) {
                logger.info("Datei umbenannt für Benutzer {}: {} -> {}", username, oldPath, newName);
                return ResponseEntity.ok("Umbenannt!");
            } else {
                logger.error("Fehler beim Umbenennen für Benutzer {}: {} -> {}", username, oldPath, newName);
                return ResponseEntity.status(500).body("Fehler beim Umbenennen!");
            }
        } catch (Exception e) {
            logger.error("Fehler beim Umbenennen", e);
            return ResponseEntity.status(500).body("Interner Serverfehler");
        }
    }

    private boolean deleteDirRecursive(File dir) {
        File[] allContents = dir.listFiles();
        if (allContents != null) {
            for (File file : allContents) deleteDirRecursive(file);
        }
        return dir.delete();
    }

    private File createZipFromDirectory(File directory) throws IOException {
        File zipFile = File.createTempFile(directory.getName(), ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zipDirectory(directory, directory.getName(), zos);
        }
        return zipFile;
    }

    private void zipDirectory(File folder, String parentFolder, ZipOutputStream zos) throws IOException {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                zipDirectory(file, parentFolder + "/" + file.getName(), zos);
            } else {
                zos.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                }
                zos.closeEntry();
            }
        }
    }
}
