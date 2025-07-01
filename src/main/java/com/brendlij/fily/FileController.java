package com.brendlij.fily;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/files")
@CrossOrigin // Für Frontend-Zugriff
public class FileController {

    @Value("${fileserver.basedir}")
    private String baseDir;

    // ==================== LISTEN ====================
    @GetMapping
    public ResponseEntity<?> listFiles(@RequestParam(defaultValue = "") String path) {
        File folder = safeFile(path);
        if (!folder.exists() || !folder.isDirectory())
            return ResponseEntity.status(404).body("Ordner nicht gefunden!");

        File[] files = folder.listFiles();
        if (files == null) files = new File[0];

        // Vater-Sohn-Hierarchie:
        List<Map<String, Object>> result = new ArrayList<>();
        for (File file : files) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", file.getName());
            entry.put("isDirectory", file.isDirectory());
            entry.put("size", file.isFile() ? file.length() : null);
            entry.put("lastModified", file.lastModified());
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    // ==================== UPLOAD ====================
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "") String path
    ) throws IOException {
        File dir = safeFile(path);
        if (!dir.exists()) dir.mkdirs();
        File dest = new File(dir, file.getOriginalFilename());
        file.transferTo(dest);
        return ResponseEntity.ok("OK");
    }

    // ==================== DOWNLOAD ====================
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam String path
    ) throws IOException {
        File file = safeFile(path);
        if (!file.exists() || file.isDirectory())
            return ResponseEntity.status(404).build();
        Resource resource = new UrlResource(file.toURI());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    // ==================== ORDNER ANLEGEN ====================
    @PostMapping("/mkdir")
    public ResponseEntity<?> makeDir(@RequestParam String path) {
        File dir = safeFile(path);
        if (dir.exists()) return ResponseEntity.status(400).body("Ordner existiert schon!");
        if (dir.mkdirs()) return ResponseEntity.ok("Ordner erstellt: " + path);
        else return ResponseEntity.status(500).body("Konnte Ordner nicht erstellen!");
    }

    // ==================== DATEI/ORDNER LÖSCHEN (rekursiv) ====================
    @DeleteMapping
    public ResponseEntity<?> deleteFileOrDir(@RequestParam String path) {
        File file = safeFile(path);
        if (!file.exists()) return ResponseEntity.status(404).body("Nicht gefunden!");
        boolean ok = file.isDirectory() ? deleteDirRecursive(file) : file.delete();
        if (ok) return ResponseEntity.ok("Gelöscht: " + path);
        else return ResponseEntity.status(500).body("Fehler beim Löschen!");
    }

    // ==================== UMBENENNEN ====================
    @PostMapping("/rename")
    public ResponseEntity<?> rename(
            @RequestParam String oldPath,
            @RequestParam String newName
    ) {
        File oldFile = safeFile(oldPath);
        if (!oldFile.exists()) return ResponseEntity.status(404).body("Nicht gefunden!");
        if (newName.contains("..") || newName.contains("/") || newName.contains("\\")) {
            return ResponseEntity.status(400).body("Ungültiger neuer Name!");
        }
        File newFile = new File(oldFile.getParentFile(), newName);
        if (newFile.exists()) return ResponseEntity.status(400).body("Zielname existiert schon!");
        boolean ok = oldFile.renameTo(newFile);
        if (ok) return ResponseEntity.ok("Umbenannt!");
        else return ResponseEntity.status(500).body("Fehler beim Umbenennen!");
    }

    // ==================== UTIL ====================
    private File safeFile(String subPath) {
        // Verhindert Path-Traversal (…/.. etc.)
        if (subPath.contains("..")) throw new IllegalArgumentException("Pfad ungültig!");
        Path fullPath = Paths.get(baseDir, subPath).normalize();
        if (!fullPath.startsWith(Paths.get(baseDir))) throw new IllegalArgumentException("Pfad ungültig!");
        return fullPath.toFile();
    }

    private boolean deleteDirRecursive(File dir) {
        File[] allContents = dir.listFiles();
        if (allContents != null) {
            for (File file : allContents) deleteDirRecursive(file);
        }
        return dir.delete();
    }
}
