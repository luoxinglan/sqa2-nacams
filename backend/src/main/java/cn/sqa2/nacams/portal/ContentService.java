package cn.sqa2.nacams.portal;

import cn.sqa2.nacams.common.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ContentService {
    public static final Set<String> TYPES=Set.of("news","achievements","courses","projects","admissions","cooperations","about");
    public static final Set<String> ACHIEVEMENTS=Set.of("教学成果","教学论文","教材","教改","科研成果","科研论文","专利","著作权");
    private final Db db;private final Json json;
    public ContentService(Db db,Json json){this.db=db;this.json=json;}
    public void checkType(String type){if(!TYPES.contains(type))throw new ApiException(404,"内容类型不存在");}
    public Map<String,Object> view(Map<String,Object> row){
        var p=json.map(Db.str(row,"data_json"));
        p.put("id",row.get("id"));p.put("type",row.get("content_type"));p.put("category",row.get("category"));
        p.put("title",row.get("title"));p.put("body",row.get("body"));p.put("author",row.get("author"));
        Object date=row.get("published_at");p.put("publishedAt",date instanceof Timestamp ts?ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")):Objects.toString(date,""));
        return p;
    }
    public Map<String,Object> list(String type,String category,String keyword,int page,int size){
        checkType(type);var args=new ArrayList<Object>();args.add(type);String from="FROM portal_content WHERE content_type=?";
        if(!category.isBlank()){from+=" AND category=?";args.add(category);}
        if(!keyword.isBlank()){from+=" AND (title LIKE ? OR body LIKE ? OR data_json LIKE ?)";for(int i=0;i<3;i++)args.add("%"+keyword+"%");}
        var result=db.page(from,args,"published_at DESC,id DESC",page,size);
        @SuppressWarnings("unchecked")var rows=(List<Map<String,Object>>)result.get("items");result.put("items",rows.stream().map(this::view).toList());return result;
    }
    public Map<String,Object> get(String type,long id){checkType(type);return view(db.one("SELECT * FROM portal_content WHERE content_type=? AND id=?",type,id));}
    @Transactional public Map<String,Object> save(String type,Long id,Map<String,Object> input){
        checkType(type);var p=new LinkedHashMap<String,Object>();if(id!=null)p.putAll(get(type,id));p.putAll(input);
        String title=Db.str(p,"title"),category=Db.str(p,"category");
        if(title.isBlank()||title.length()>500)throw new ApiException(422,"标题必填且≤500字符");
        if(type.equals("news")&&!Set.of("新闻动态","通知通告","学术活动").contains(category))throw new ApiException(422,"动态栏目不合法");
        if(type.equals("achievements")&&!ACHIEVEMENTS.contains(category))throw new ApiException(422,"成果栏目不合法");
        if(type.equals("admissions")&&!Set.of("本科","硕士","博士","留学生").contains(category))throw new ApiException(422,"招生层次不合法");
        Timestamp published=Timestamp.valueOf(LocalDateTime.now());
        if(!Db.str(p,"publishedAt").isBlank())published=Timestamp.valueOf(LocalDateTime.parse(Db.str(p,"publishedAt"),DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        var data=new LinkedHashMap<>(p);for(String key:List.of("id","type","title","body","author","category","publishedAt","sourceKey"))data.remove(key);
        if(id==null)id=db.insert("INSERT INTO portal_content(content_type,category,title,body,author,published_at,data_json,source_key) VALUES(?,?,?,?,?,?,?,?)",type,category,title,Db.str(p,"body"),Db.str(p,"author"),published,json.write(data),p.get("sourceKey"));
        else db.jdbc.update("UPDATE portal_content SET category=?,title=?,body=?,author=?,published_at=?,data_json=? WHERE id=? AND content_type=?",category,title,Db.str(p,"body"),Db.str(p,"author"),published,json.write(data),id,type);
        return get(type,id);
    }
    public void delete(String type,long id){checkType(type);if(db.jdbc.update("DELETE FROM portal_content WHERE id=? AND content_type=?",id,type)==0)throw new ApiException(404,"记录不存在");}
}
