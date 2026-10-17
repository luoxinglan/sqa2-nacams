package cn.sqa2.nacams.admin;

import cn.sqa2.nacams.auth.Access;
import cn.sqa2.nacams.common.Api;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin")
public class ImportController {
    private final Access access;private final ImportService imports;
    public ImportController(Access access,ImportService imports){this.access=access;this.imports=imports;}
    @PostMapping("/users/import")public Api<?> users(@RequestParam MultipartFile file){access.admin();return Api.ok(imports.users(file));}
    @PostMapping("/content/{type}/import")public Api<?> content(@PathVariable String type,@RequestParam MultipartFile file){access.admin();return Api.ok(imports.content(type,file));}
    @PostMapping("/import/baseline")public Api<?> baseline(@RequestParam String kind,@RequestParam MultipartFile file,
            @RequestParam(defaultValue="2026")int purchaseYear,@RequestParam(defaultValue="")String initialPassword,
            @RequestParam(defaultValue="2026-10-17")LocalDate demoDate){access.admin();return Api.ok(imports.baseline(kind,file,purchaseYear,initialPassword,demoDate));}
}
