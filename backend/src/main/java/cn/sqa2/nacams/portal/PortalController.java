package cn.sqa2.nacams.portal;

import cn.sqa2.nacams.admin.UserService;
import cn.sqa2.nacams.auth.Access;
import cn.sqa2.nacams.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class PortalController {
    private final ContentService content;private final Db db;private final UserService users;private final Access access;private final MediaService media;
    private final String name,englishName,logo,copyright;
    public PortalController(ContentService content,Db db,UserService users,Access access,MediaService media,
            @Value("${app.site.name}")String name,@Value("${app.site.english-name}")String englishName,
            @Value("${app.site.logo}")String logo,@Value("${app.site.copyright}")String copyright){
        this.content=content;this.db=db;this.users=users;this.access=access;this.media=media;this.name=name;this.englishName=englishName;this.logo=logo;this.copyright=copyright;
    }
    @GetMapping("/site/info")public Api<?> site(){return Api.ok(Map.of("name",name,"englishName",englishName,"logo",logo,"copyright",copyright,
            "links",Map.of("cs","https://cs.bit.edu.cn","grd","https://grd.bit.edu.cn","gms","https://gms.bit.edu.cn")));}
    @GetMapping("/home/summary")public Api<?> home(){return Api.ok(Map.of("news",content.list("news","","",1,5).get("items"),
            "notificationCount",db.jdbc.queryForObject("SELECT COUNT(*) FROM portal_content WHERE content_type='news' AND category='通知通告'",Long.class)));}
    @GetMapping({"/news","/achievements","/courses","/projects","/admissions","/cooperations","/about"})public Api<?> list(
            jakarta.servlet.http.HttpServletRequest request,@RequestParam(defaultValue="")String category,@RequestParam(defaultValue="")String type,
            @RequestParam(defaultValue="")String level,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="1")int page,
            @RequestParam(name="page_size",defaultValue="20")int size){
        String module=request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/')+1);
        String filter=module.equals("achievements")?type:module.equals("admissions")?level:category;
        return Api.ok(content.list(module,filter,keyword,page,size));
    }
    @GetMapping("/news/{id}")public Api<?> news(@PathVariable long id){return Api.ok(content.get("news",id));}
    @GetMapping({"/teachers","/students"})public Api<?> people(jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(defaultValue="")String type,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String year,
            @RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(name="page_size",defaultValue="20")int size){
        boolean teacher=request.getRequestURI().endsWith("teachers");var args=new ArrayList<Object>();
        String from="FROM users u JOIN "+(teacher?"teachers t":"students s")+" ON u.id="+(teacher?"t":"s")+".user_id WHERE u.active=TRUE";
        if(teacher&&!type.isBlank()){
            if(!Set.of("全职","兼职").contains(type))throw new ApiException(400,"type为全职或兼职");
            from+=" AND t.teacher_type=?";args.add(type.equals("全职")?"full":"external");
        }
        if(!teacher&&!status.isBlank()){from+=" AND s.status=?";args.add(status);}
        if(!teacher&&!year.isBlank()){from+=" AND s.enrollment_year=?";args.add(Integer.parseInt(year));}
        if(!keyword.isBlank()){from+=" AND (u.name LIKE ? OR "+(teacher?"t.employee_no":"s.student_no")+" LIKE ?)";args.add("%"+keyword+"%");args.add("%"+keyword+"%");}
        int offset=Db.page(page,size);long total=db.jdbc.queryForObject("SELECT COUNT(*) "+from,Long.class,args.toArray());
        args.add(size);args.add(offset);
        var ids=db.jdbc.queryForList("SELECT u.id "+from+" ORDER BY u.id LIMIT ? OFFSET ?",args.toArray());
        return Api.ok(Map.of("items",ids.stream().map(row->users.publicView(Db.id(row.get("id")))).toList(),"total",total,"page",page,"page_size",size));
    }
    @GetMapping("/teachers/{id}")public Api<?> teacher(@PathVariable long id){
        db.one("SELECT user_id FROM teachers WHERE user_id=? AND user_id IN(SELECT id FROM users WHERE active=TRUE)",id);return Api.ok(users.publicView(id));
    }
    @GetMapping("/students/{id}")public Api<?> student(@PathVariable long id){
        db.one("SELECT user_id FROM students WHERE user_id=? AND user_id IN(SELECT id FROM users WHERE active=TRUE)",id);return Api.ok(users.publicView(id));
    }
    @PostMapping("/admin/content/{type}")public Api<?> create(@PathVariable String type,@RequestBody Map<String,Object> p){access.admin();return Api.ok(content.save(type,null,p));}
    @PutMapping("/admin/content/{type}/{id}")public Api<?> update(@PathVariable String type,@PathVariable long id,@RequestBody Map<String,Object> p){access.admin();return Api.ok(content.save(type,id,p));}
    @DeleteMapping("/admin/content/{type}/{id}")public Api<?> delete(@PathVariable String type,@PathVariable long id){access.admin();content.delete(type,id);return Api.ok(null);}
    @GetMapping("/admin/content/{type}")public Api<?> adminList(@PathVariable String type,@RequestParam(defaultValue="")String keyword,
            @RequestParam(defaultValue="1")int page,@RequestParam(name="page_size",defaultValue="20")int size){access.admin();return Api.ok(content.list(type,"",keyword,page,size));}
    @PostMapping("/admin/media")public Api<?> upload(@RequestParam MultipartFile file){access.admin();return Api.ok(media.save(file));}
    @GetMapping("/media/{id}")public ResponseEntity<?> file(@PathVariable String id){
        var p=media.metadata(id);if(!Db.bool(p.get("is_public")))access.user();String mime=Db.str(p,"content_type");
        boolean inline=mime.startsWith("image/")||mime.equals("video/mp4");
        return ResponseEntity.ok().header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION,(inline?ContentDisposition.inline():ContentDisposition.attachment()).filename(Db.str(p,"original_name"),StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(mime)).body(new FileSystemResource(media.path(Db.str(p,"storage_name"))));
    }
}
