package cn.sqa2.nacams.backup;

import cn.sqa2.nacams.common.*;
import cn.sqa2.nacams.portal.MediaService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** 受控逻辑备份：仅接受本系统JSON格式，不执行上传SQL。数据库恢复与媒体文件一起处理。 */
@Service
public class BackupService {
    private static final Logger LOG=LoggerFactory.getLogger(BackupService.class);
    public static final List<String> TABLES=List.of("users","teachers","students","signin_logs","books","borrow_requests","borrows",
            "violations","notifications","library_config","theses","patents","projects","courses","textbooks","portal_content","media_files","downloads","source_import_rows");
    private final Db db;private final ObjectMapper mapper;private final MediaService media;private final TransactionTemplate tx;
    public final Path root;private final String cron;
    public BackupService(Db db,ObjectMapper mapper,MediaService media,PlatformTransactionManager manager,
            @Value("${app.storage}")String storage,@Value("${app.backup.cron}")String cron)throws IOException{
        this.db=db;this.mapper=mapper;this.media=media;this.tx=new TransactionTemplate(manager);this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.root=Path.of(storage).toAbsolutePath().normalize().resolve("backups");Files.createDirectories(root);this.cron=cron;
    }
    @Scheduled(cron="${app.backup.cron}",zone="Asia/Shanghai") public void scheduled(){
        try{create();}catch(Exception e){LOG.error("自动备份失败，请检查磁盘与数据库",e);}
    }
    public synchronized Map<String,Object> create(){
        Map<String,Object> snapshot=tx.execute(status->{
            var tables=new LinkedHashMap<String,Object>();
            for(String table:TABLES){var rows=db.jdbc.queryForList("SELECT * FROM "+table);
                for(var row:rows)row.replaceAll((k,v)->v instanceof Timestamp ts?ts.toLocalDateTime().toString():v);
                tables.put(table,rows);}
            var files=new LinkedHashMap<String,String>();
            for(var row:db.jdbc.queryForList("SELECT storage_name FROM media_files")){
                String name=Db.str(row,"storage_name");try{files.put(name,Base64.getEncoder().encodeToString(Files.readAllBytes(media.path(name))));}
                catch(IOException e){throw new ApiException(409,"媒体文件缺失，备份已取消");}
            }
            return Map.<String,Object>of("format","SQA2-NACAMS","schemaVersion",1,"createdAt",LocalDateTime.now().toString(),"tables",tables,"files",files);
        });
        String name="backup-"+UUID.randomUUID()+".json";Path target=root.resolve(name),temp=root.resolve(name+".tmp");
        try{mapper.writeValue(temp.toFile(),snapshot);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);return info(target);}
        catch(IOException e){throw new ApiException(500,"备份写入失败");}
    }
    public Map<String,Object> list(){
        try(var stream=Files.list(root)){return Map.of("items",stream.filter(p->p.getFileName().toString().endsWith(".json")).sorted().map(this::info).toList(),
                "schedule",cron,"zone","Asia/Shanghai","location",root.toString(),"retention","不自动删除；交付期每日02:00备份，管理员按存储情况人工归档");}
        catch(IOException e){throw new ApiException(500,"备份目录读取失败");}
    }
    private Map<String,Object> info(Path path){
        try{return Map.of("fileName",path.getFileName().toString(),"size",Files.size(path),"createdAt",Files.getLastModifiedTime(path).toInstant().toString());}
        catch(IOException e){throw new ApiException(500,"备份信息读取失败");}
    }
    public Path file(String name){if(!name.matches("backup-[0-9a-f-]{36}\\.json"))throw new ApiException(400,"备份名称不合法");
        Path path=root.resolve(name);if(!Files.isRegularFile(path))throw new ApiException(404,"备份不存在");return path;}
    @SuppressWarnings("unchecked")
    public synchronized Map<String,Object> restore(MultipartFile upload,boolean confirmed){
        if(!confirmed)throw new ApiException(409,"恢复将替换全部业务数据并使登录失效，请先确认后传confirm=true");
        if(upload.getSize()>50L*1024*1024)throw new ApiException(422,"恢复文件不能超过50MB");
        Map<String,Object> snapshot;
        try{snapshot=mapper.readValue(upload.getBytes(),new TypeReference<LinkedHashMap<String,Object>>(){});}
        catch(Exception e){throw new ApiException(422,"不是有效的备份JSON");}
        if(!"SQA2-NACAMS".equals(snapshot.get("format"))||!Integer.valueOf(1).equals(snapshot.get("schemaVersion")))throw new ApiException(422,"备份格式或版本不兼容");
        if(!(snapshot.get("tables") instanceof Map<?,?> rawTables)||!rawTables.keySet().equals(new HashSet<>(TABLES)))throw new ApiException(422,"备份表集合不完整");
        var tables=(Map<String,List<Map<String,Object>>>)rawTables;
        var columns=new HashMap<String,Set<String>>();
        for(String table:TABLES){
            Set<String> names=db.jdbc.query("SELECT * FROM "+table+" WHERE 1=0",rs->{var set=new HashSet<String>();var meta=rs.getMetaData();for(int i=1;i<=meta.getColumnCount();i++)set.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));return set;});
            columns.put(table,names);
            Object rows=rawTables.get(table);if(!(rows instanceof List<?> list))throw new ApiException(422,"备份行格式错误");
            for(Object value:list)if(!(value instanceof Map<?,?> row)||!row.keySet().equals(names))throw new ApiException(422,"备份字段与当前schema不一致："+table);
        }
        if(tables.get("users").stream().noneMatch(u->"admin".equals(u.get("role"))&&Db.bool(u.get("active"))))throw new ApiException(422,"备份中没有有效管理员");
        if(!(snapshot.get("files") instanceof Map<?,?>))throw new ApiException(422,"备份缺少媒体文件集合");
        var encoded=(Map<String,String>)snapshot.get("files");var decoded=new HashMap<String,byte[]>();
        try{for(var row:tables.get("media_files")){String name=Db.str(row,"storage_name");media.path(name);String data=encoded.get(name);
                if(data==null)throw new ApiException(422,"媒体备份缺失");byte[] bytes=Base64.getDecoder().decode(data);
                if(bytes.length!=Db.id(row.get("size_bytes")))throw new ApiException(422,"媒体大小校验失败");decoded.put(name,bytes);}}
        catch(IllegalArgumentException e){throw new ApiException(422,"媒体编码不合法");}
        var before=create();var written=new ArrayList<Path>();
        try{
            // 写新文件名，恢复失败时不覆盖当前媒体。
            for(var row:tables.get("media_files")){String old=Db.str(row,"storage_name"),fresh=UUID.randomUUID()+"-restored.bin";
                Path path=media.path(fresh);Files.write(path,decoded.get(old),StandardOpenOption.CREATE_NEW);written.add(path);row.put("storage_name",fresh);}
            tx.execute(status->{
                db.jdbc.update("DELETE FROM auth_sessions");var reversed=new ArrayList<>(TABLES);Collections.reverse(reversed);
                for(String table:reversed)db.jdbc.update("DELETE FROM "+table);
                for(String table:TABLES)for(var row:tables.get(table)){
                    var keys=new ArrayList<>(row.keySet());String fields=String.join(",",keys);
                    var values=keys.stream().map(k->convert(k,row.get(k))).toArray();
                    db.jdbc.update("INSERT INTO "+table+" ("+fields+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",values);
                }
                db.jdbc.update("UPDATE signin_logs SET signout_at=CURRENT_TIMESTAMP WHERE signout_at IS NULL");return null;
            });
        }catch(Exception e){for(Path p:written)try{Files.deleteIfExists(p);}catch(IOException ignored){}if(e instanceof ApiException a)throw a;throw new ApiException(422,"恢复失败，数据库事务已回滚");}
        return Map.of("restored",true,"preRestoreBackup",before.get("fileName"),"loginRequired",true);
    }
    private Object convert(String column,Object value){
        if(value instanceof String s && (column.endsWith("_at")))return Timestamp.valueOf(LocalDateTime.parse(s));
        return value;
    }
}
