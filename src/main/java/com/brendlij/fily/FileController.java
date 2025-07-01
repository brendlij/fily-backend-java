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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    public ResponseEntity<Resource> downloadFile(@RequestParam String path) throws IOException {
        File file = safeFile(path);
        if (!file.exists()) return ResponseEntity.status(404).build();

        if (file.isDirectory()) {
            // Ordner als ZIP komprimieren
            File zipFile = createZipFromDirectory(file);
            Resource resource = new UrlResource(zipFile.toURI());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getName() + ".zip\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                    .body(resource);
        } else {
            // Normale Datei
            Resource resource = new UrlResource(file.toURI());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        }
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
