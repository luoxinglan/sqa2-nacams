package cn.sqa2.nacams.admin;

import cn.sqa2.nacams.auth.Access;
import cn.sqa2.nacams.common.Api;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import cn.sqa2.nacams.common.Db;
import java.util.ArrayList;

@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
@RestController
@RequestMapping("/api/v1/admin")
public class ImportController {
    private final Access access;private final ImportService imports;private final Db db;
    public ImportController(Access access,ImportService imports,Db db){this.access=access;this.imports=imports;this.db=db;}
    @PostMapping("/users/import")public Api<?> users(@RequestParam MultipartFile file){access.admin();return Api.ok(imports.users(file));}
    @PostMapping("/content/{type}/import")public Api<?> content(@PathVariable String type,@RequestParam MultipartFile file){access.admin();return Api.ok(imports.content(type,file));}
    @PostMapping("/import/baseline")public Api<?> baseline(@RequestParam String kind,@RequestParam MultipartFile file,
            @RequestParam(defaultValue="2026")int purchaseYear,@RequestParam(defaultValue="")String initialPassword,
            @RequestParam(defaultValue="2026-10-17")LocalDate demoDate){access.admin();return Api.ok(imports.baseline(kind,file,purchaseYear,initialPassword,demoDate));}
    @GetMapping("/import/rows")public Api<?> rows(@RequestParam(defaultValue="")String kind,@RequestParam(defaultValue="")String status,
            @RequestParam(defaultValue="1")int page,@RequestParam(name="page_size",defaultValue="20")int size){
        access.admin();String from="FROM source_import_rows WHERE 1=1";var args=new ArrayList<Object>();
        if(!kind.isBlank()){from+=" AND kind=?";args.add(kind);}if(!status.isBlank()){from+=" AND status=?";args.add(status);}
        return Api.ok(db.page(from,args,"kind,sheet_name,source_row_no",page,size));
    }
}
