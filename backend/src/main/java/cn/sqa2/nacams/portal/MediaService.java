package cn.sqa2.nacams.portal;

import cn.sqa2.nacams.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class MediaService {
    public final Path root;
    private final Db db;
    private static final Map<String,String> TYPES=Map.ofEntries(Map.entry("png","image/png"),Map.entry("jpg","image/jpeg"),Map.entry("jpeg","image/jpeg"),
            Map.entry("gif","image/gif"),Map.entry("webp","image/webp"),Map.entry("mp4","video/mp4"),Map.entry("pdf","application/pdf"));
    public MediaService(Db db,@Value("${app.storage}")String storage)throws IOException{this.db=db;this.root=Path.of(storage).toAbsolutePath().normalize().resolve("media");Files.createDirectories(root);}
    public Map<String,Object> savePhoto(MultipartFile file){
        if(file.getSize()>2L*1024*1024)throw new ApiException(422,"照片必须≤2MB",Map.of("fields",Map.of("photo","超过2MB")));
        try(var in=file.getInputStream()){
            var image=ImageIO.read(in);
            if(image==null || (long)image.getWidth()*48!=(long)image.getHeight()*33)throw new ApiException(422,"照片宽高比必须为33:48",Map.of("fields",Map.of("photo","宽高比错误或图片不可读")));
        }catch(IOException e){throw new ApiException(422,"图片不可读");}
        return save(file);
    }
    public Map<String,Object> save(MultipartFile file){
        return save(file,true);
    }
    public Map<String,Object> savePrivate(MultipartFile file){return save(file,false);}
    private Map<String,Object> save(MultipartFile file,boolean isPublic){
        if(file.isEmpty()||file.getSize()>50L*1024*1024)throw new ApiException(422,"文件为空或超过50MB");
        String name=Objects.toString(file.getOriginalFilename(),"file").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);
        if(name.length()>255||name.contains("\r")||name.contains("\n"))throw new ApiException(422,"文件名不合法");
        String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
        if(!Set.of("png","jpg","jpeg","gif","webp","mp4","pdf","doc","docx","xls","xlsx","csv","txt","zip","ppt","pptx").contains(ext))throw new ApiException(422,"不支持的文件类型");
        String id=UUID.randomUUID().toString(),storageName=id+"."+ext,type=TYPES.getOrDefault(ext,"application/octet-stream");
        Path target=root.resolve(storageName);
        try{file.transferTo(target);db.jdbc.update("INSERT INTO media_files(id,original_name,storage_name,content_type,size_bytes,is_public) VALUES(?,?,?,?,?,?)",id,name,storageName,type,file.getSize(),isPublic);}
        catch(Exception e){try{Files.deleteIfExists(target);}catch(IOException ignored){}throw new ApiException(500,"文件保存失败");}
        return Map.of("id",id,"url","/api/v1/media/"+id,"name",name,"size",file.getSize(),"contentType",type);
    }
    public Path path(String name){Path p=root.resolve(name).normalize();if(!p.getParent().equals(root))throw new ApiException(400,"无效文件路径");return p;}
    public Map<String,Object> metadata(String id){return db.one("SELECT * FROM media_files WHERE id=?",id);}
}
