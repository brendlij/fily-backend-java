package com.brendlij.fily;

import com.brendlij.fily.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

@RestController
@RequestMapping("/api/files")
public class FileController {

    // DTO für die Anfrage
    public static class MoveFileRequest {
        private String source;
        private String target;
        // Getter & Setter
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
    }


    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Value("${fileserver.basedir}")
    private String baseDir;

    // Hilfsmethode: Hole den Benutzernamen aus dem Spring Security Context
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            logger.warn("Nicht authentifiziert!");
            throw new RuntimeException("Nicht authentifiziert!");
        }
        return auth.getName();
    }

    // Verhindert Directory Traversal und normalisiert Pfad
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
    public ResponseEntity<?> listFiles(@RequestParam(defaultValue = "") String path) {
        try {
            String username = getCurrentUsername();
            String currentPath = path.isEmpty() ? "" : path;
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
                    entry.put("path", currentPath.isEmpty() ? file.getName() : currentPath + "/" + file.getName());
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
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "") String path) {
        try {
            String username = getCurrentUsername();
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
    public ResponseEntity<Resource> downloadFile(@RequestParam String path) {
        try {
            String username = getCurrentUsername();
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
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(resource);

        } catch (Exception e) {
            logger.error("Fehler beim Download", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/view")
    public ResponseEntity<Resource> viewFileInline(@RequestParam String path) {
        try {
            String username = getCurrentUsername();
            File file = safeFile(username, path);

            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(file.toURI());

            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                String ext = "";
                int i = file.getName().lastIndexOf('.');
                if (i > 0) {
                    ext = file.getName().substring(i + 1).toLowerCase();
                }
                switch (ext) {
                    case "pdf": contentType = "application/pdf"; break;
                    case "png": contentType = "image/png"; break;
                    case "jpg":
                    case "jpeg": contentType = "image/jpeg"; break;
                    case "gif": contentType = "image/gif"; break;
                    case "bmp": contentType = "image/bmp"; break;
                    case "svg": contentType = "image/svg+xml"; break;
                    case "txt": contentType = "text/plain"; break;
                    case "json": contentType = "application/json"; break;
                    case "csv": contentType = "text/csv"; break;
                    case "html":
                    case "htm": contentType = "text/html"; break;
                    case "md": contentType = "text/markdown"; break;
                    case "mp4": contentType = "video/mp4"; break;
                    case "avi": contentType = "video/x-msvideo"; break;
                    case "mov": contentType = "video/quicktime"; break;
                    default: contentType = "application/octet-stream"; break;
                }
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(resource);

        } catch (Exception e) {
            logger.error("Fehler bei Datei-View", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/mkdir")
    public ResponseEntity<?> makeDir(@RequestParam String path) {
        try {
            String username = getCurrentUsername();
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
    public ResponseEntity<?> deleteFileOrDir(@RequestParam String path) {
        try {
            String username = getCurrentUsername();
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
    public ResponseEntity<?> rename(@RequestParam String oldPath, @RequestParam String newName) {
        try {
            String username = getCurrentUsername();
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

    // Move-Endpoint (einfach zu FileController hinzufügen)
    @PostMapping("/move")
    public ResponseEntity<?> moveFile(@RequestBody MoveFileRequest request) {
        try {
            String username = getCurrentUsername();

            // Source und Target (beide relativ zum User-Ordner!)
            File sourceFile = safeFile(username, request.getSource());
            File targetFile = safeFile(username, request.getTarget());

            // Existiert die Quelldatei?
            if (!sourceFile.exists()) {
                return ResponseEntity.status(404)
                        .body(Collections.singletonMap("error", "Quelle nicht gefunden!"));
            }

            // Zielverzeichnis anlegen, falls nötig
            File targetDir = targetFile.getParentFile();
            if (!targetDir.exists()) {
                if (!targetDir.mkdirs()) {
                    return ResponseEntity.status(500)
                            .body(Collections.singletonMap("error", "Zielordner konnte nicht erstellt werden!"));
                }
            }

            // Verschieben!
            boolean ok = sourceFile.renameTo(targetFile);
            if (ok) {
                logger.info("Datei/Ordner verschoben von {} nach {} für {}", sourceFile, targetFile, username);
                return ResponseEntity.ok(Collections.singletonMap("message", "Verschoben!"));
            } else {
                logger.error("Verschieben fehlgeschlagen: {} -> {} für {}", sourceFile, targetFile, username);
                return ResponseEntity.status(500)
                        .body(Collections.singletonMap("error", "Verschieben fehlgeschlagen!"));
            }
        } catch (Exception e) {
            logger.error("Fehler beim Verschieben", e);
            return ResponseEntity.status(500)
                    .body(Collections.singletonMap("error", "Interner Fehler beim Verschieben!"));
        }
    }


    // --- Hilfsfunktionen ---

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
