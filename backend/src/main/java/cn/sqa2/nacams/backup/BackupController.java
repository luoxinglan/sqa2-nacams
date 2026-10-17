package cn.sqa2.nacams.backup;

import cn.sqa2.nacams.auth.Access;
import cn.sqa2.nacams.common.Api;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
@RestController
@RequestMapping("/api/v1/admin")
public class BackupController {
    private final Access access;private final BackupService backup;
    public BackupController(Access access,BackupService backup){this.access=access;this.backup=backup;}
    @PostMapping("/backup")public Api<?> create(){access.admin();return Api.ok(backup.create());}
    @GetMapping("/backups")public Api<?> list(){access.admin();return Api.ok(backup.list());}
    @GetMapping("/backups/{fileName}/file")public ResponseEntity<?> file(@PathVariable String fileName){access.admin();return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(fileName).build().toString())
            .contentType(MediaType.APPLICATION_JSON).body(new FileSystemResource(backup.file(fileName)));}
    @PostMapping("/restore")public Api<?> restore(@RequestParam MultipartFile file,@RequestParam(defaultValue="false")boolean confirm){access.admin();return Api.ok(backup.restore(file,confirm));}
}
