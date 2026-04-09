package io.github.claudetoolkit.ui.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.*;

/**
 * 데이터 백업/복원 컨트롤러 (ADMIN 전용).
 * H2 DB + settings.json + security-settings.json을 ZIP으로 백업/복원.
 */
@Controller
@RequestMapping("/admin/backup")
public class BackupController {

    private static final Path TOOLKIT_DIR = Paths.get(System.getProperty("user.home"), ".claude-toolkit");

    @GetMapping
    public String page() { return "admin/backup"; }

    /** ZIP 다운로드 */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        addFileToZip(zos, TOOLKIT_DIR.resolve("history-db.mv.db"), "history-db.mv.db");
        addFileToZip(zos, TOOLKIT_DIR.resolve("settings.json"), "settings.json");
        addFileToZip(zos, TOOLKIT_DIR.resolve("security-settings.json"), "security-settings.json");

        zos.close();

        String filename = "claude-toolkit-backup-" + LocalDate.now() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(baos.toByteArray());
    }

    /** ZIP 업로드 복원 */
    @PostMapping("/restore")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restore(@RequestParam("file") MultipartFile file) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            byte[] data = file.getBytes();
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data));
            ZipEntry entry;
            int restored = 0;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // 허용된 파일만 복원
                if ("history-db.mv.db".equals(name) || "settings.json".equals(name)
                        || "security-settings.json".equals(name)) {
                    Files.createDirectories(TOOLKIT_DIR);
                    Path target = TOOLKIT_DIR.resolve(name);
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    restored++;
                }
                zis.closeEntry();
            }
            zis.close();
            resp.put("success", true);
            resp.put("restoredFiles", restored);
            resp.put("message", restored + "개 파일 복원 완료. 재시작 후 적용됩니다.");
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    private void addFileToZip(ZipOutputStream zos, Path file, String entryName) throws IOException {
        if (!Files.exists(file)) return;
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zos);
        zos.closeEntry();
    }
}
