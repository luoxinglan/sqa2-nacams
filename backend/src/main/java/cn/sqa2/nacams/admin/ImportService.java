package cn.sqa2.nacams.admin;

import cn.sqa2.nacams.auth.AuthService;
import cn.sqa2.nacams.common.*;
import cn.sqa2.nacams.portal.ContentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class ImportService {
    private final Db db;private final Json json;private final UserService users;private final TabularReader reader;private final ContentService content;
    private final TransactionTemplate tx;
    public ImportService(Db db,Json json,UserService users,TabularReader reader,ContentService content,PlatformTransactionManager manager){
        this.db=db;this.json=json;this.users=users;this.reader=reader;this.content=content;tx=new TransactionTemplate(manager);
    }
    public Map<String,Object> users(MultipartFile file){return process(reader.read(file),row->{var values=new LinkedHashMap<String,Object>(row.values());normalizeNameExempt(values);return users.save(null,values);});}
    public Map<String,Object> content(String type,MultipartFile file){content.checkType(type);return process(reader.read(file),row->content.save(type,null,row.values()));}
    private Map<String,Object> process(List<TabularReader.Row> rows,java.util.function.Function<TabularReader.Row,Object> operation){
        var results=new ArrayList<Map<String,Object>>();int success=0;
        for(var row:rows){try{Object value=tx.execute(status->operation.apply(row));results.add(Map.of("sheet",row.sheet(),"row",row.number(),"ok",true,"reason","成功","data",value));success++;}
            catch(Exception e){results.add(Map.of("sheet",row.sheet(),"row",row.number(),"ok",false,"reason",reason(e)));}}
        return Map.of("total",rows.size(),"success",success,"failed",rows.size()-success,"rows",results);
    }
    private String reason(Exception e){return e instanceof ApiException a?a.getMessage()+(a.data==null?"":"："+json.write(a.data)):e instanceof org.springframework.dao.DataIntegrityViolationException?"编号重复或引用关系不满足":"该行数据不合法";}
    public Map<String,Object> baseline(String kind,MultipartFile file,int purchaseYear,String initialPassword,LocalDate demoDate){
        if(!Set.of("teachers","students","books","borrows","theses","patents","projects","courses","textbooks").contains(kind))throw new ApiException(400,"未知基础数据类型");
        if(purchaseYear<1900||purchaseYear>2100)throw new ApiException(422,"购买年份需在1900至2100之间");
        if((kind.equals("teachers")||kind.equals("students"))&&initialPassword.length()<8)throw new ApiException(422,"导入教师/学生必须提供至少8位的initialPassword");
        var rows=reader.read(file);var results=new ArrayList<Map<String,Object>>();int success=0,skipped=0;
        for(var row:rows){
            String raw=json.write(row.values()),key=kind+":"+AuthService.hash(row.sheet()+":"+row.number()+":"+raw);
            if(db.jdbc.queryForObject("SELECT COUNT(*) FROM source_import_rows WHERE source_key=? AND status='imported'",Long.class,key)>0){
                results.add(Map.of("sheet",row.sheet(),"row",row.number(),"ok",true,"reason","已导入，跳过"));skipped++;continue;
            }
            try{
                tx.execute(status->{importRow(kind,row,key,purchaseYear,initialPassword,demoDate);journal(key,kind,row,raw,"imported","");return null;});
                results.add(Map.of("sheet",row.sheet(),"row",row.number(),"ok",true,"reason","成功"));success++;
            }catch(Exception e){String why=reason(e);journal(key,kind,row,raw,"rejected",why);results.add(Map.of("sheet",row.sheet(),"row",row.number(),"ok",false,"reason",why));}
        }
        return Map.of("total",rows.size(),"success",success,"skipped",skipped,"failed",rows.size()-success-skipped,"rows",results);
    }
    private void journal(String key,String kind,TabularReader.Row row,String raw,String status,String reason){
        int updated=db.jdbc.update("UPDATE source_import_rows SET status=?,reason=?,imported_at=CURRENT_TIMESTAMP WHERE source_key=?",status,reason,key);
        if(updated==0)db.jdbc.update("INSERT INTO source_import_rows(source_key,kind,sheet_name,source_row_no,raw_json,status,reason) VALUES(?,?,?,?,?,?,?)",key,kind,row.sheet(),row.number(),raw,status,reason);
    }
    private String val(Map<String,Object> p,String key){return Db.str(p,key);}
    /** 通用导入：把"是/否/1/0/yes/no/true/false"归一化为布尔，空值移除该键，其他值整行拒绝。 */
    private void normalizeNameExempt(Map<String,Object> p){
        if(!p.containsKey("nameExempt"))return;
        Object raw=p.get("nameExempt");String value=raw==null?"":raw.toString().trim();
        if(value.isEmpty()){p.remove("nameExempt");return;}
        p.put("nameExempt",parseNameExempt(value));
    }
    /** 基础数据导入的"姓名豁免"列取值，非法值抛 422 由行级事务隔离。 */
    private Boolean parseNameExempt(String value){
        String v=value.trim().toLowerCase(Locale.ROOT);
        if(Set.of("true","是","1","yes","y").contains(v))return Boolean.TRUE;
        if(Set.of("false","否","0","no","n").contains(v))return Boolean.FALSE;
        throw new ApiException(422,"姓名豁免列仅支持是/否");
    }
    private void importRow(String kind,TabularReader.Row row,String key,int year,String password,LocalDate date){
        var p=row.values();String raw=json.write(p),title="";
        switch(kind){
            case "teachers","students"->{
                boolean teacher=kind.equals("teachers");var profile=new LinkedHashMap<String,Object>();
                String number=val(p,teacher?"工号":"学号");profile.put("username",number);profile.put("password",password);
                // 姓名保留单元格原文：首尾空白由校验层拒绝，不做静默 trim/改写。
                profile.put("identityType",teacher?"teacher":"student");profile.put("name",Objects.toString(p.get("姓名"),""));
                if(teacher){profile.put("employeeNo",number);profile.put("teacherType","full");profile.put("title",val(p,"职称"));
                    profile.put("supervisorQualification",val(p,"导师资格"));profile.put("position",val(p,"研究所职务"));profile.put("unit",val(p,"学院"));
                    profile.put("socialPositions",val(p,"社会兼职"));profile.put("collegePosition",val(p,"学院行政职务"));profile.put("collegeAcademicPosition",val(p,"学院学术职务"));
                }else{profile.put("studentNo",number);profile.put("status",row.sheet().contains("在读")?"在读":row.sheet().contains("结业")?"结业肄业":"毕业");
                    profile.put("enrollmentYear",Integer.parseInt(val(p,"年级")));profile.put("advisor",val(p,"导师"));
                    profile.put("enrollmentDate",val(p,"入学时间"));profile.put("graduationDate",val(p,"毕业时间"));profile.put("category",val(p,"类别"));
                    String exempt=val(p,"姓名豁免");if(exempt.isEmpty())exempt=val(p,"nameExempt");
                    if(!exempt.isEmpty())profile.put("nameExempt",parseNameExempt(exempt));}
                profile.put("gender",val(p,"性别"));profile.put("remark",val(p,"备注"));
                // 缺失字段保留缺失，不把虚构电话/生日混入老师原始数据。
                var existing=db.jdbc.queryForList("SELECT id FROM users WHERE username=?",number);
                Long id=existing.isEmpty()?null:Db.id(existing.get(0).get("id"));
                var saved=users.save(id,profile);db.jdbc.update("UPDATE "+kind+" SET source_json=? WHERE user_id=?",raw,saved.get("id"));
            }
            case "books"->{String legacy=val(p,"图书编号");if(!legacy.matches("B\\d{3}"))throw new ApiException(422,"图书原编号应为B001形式");
                int sequence=Integer.parseInt(legacy.substring(1));String code=String.format("%04d%03d",year,sequence);
                db.jdbc.update("INSERT INTO books(code,legacy_code,title,purchase_year,location,source_json) VALUES(?,?,?,?,?,?)",code,legacy,val(p,"图书名称"),year,"研究所文件柜",raw);
            }
            case "borrows"->{
                var book=db.one("SELECT id FROM books WHERE legacy_code=?",val(p,"编号"));
                var matches=db.jdbc.queryForList("SELECT id FROM users WHERE name=? AND identity_type IN('teacher','student')",val(p,"借阅人"));
                if(matches.size()!=1)throw new ApiException(422,"借阅人未匹配或重名；原始行已保留，请人工核对");
                // 老师无日期数据：只形成明确标识的已归还历史演示记录，绝不伪造在借/违规。
                var returned=date.minusDays((long)row.number()*2);var borrowed=returned.minusDays(1);
                int purchased=((Number)db.one("SELECT purchase_year FROM books WHERE id=?",book.get("id")).get("purchase_year")).intValue();
                if(borrowed.isBefore(LocalDate.of(purchased,1,1)))throw new ApiException(422,"演示借出日期早于购书年份，请调整demoDate或购买年份假设");
                db.jdbc.update("INSERT INTO borrows(user_id,book_id,borrowed_at,due_at,returned_at,source_key,source_json) VALUES(?,?,?,?,?,?,?)",matches.get(0).get("id"),book.get("id"),Timestamp.valueOf(borrowed.atStartOfDay()),Timestamp.valueOf(borrowed.plusDays(30).atStartOfDay()),Timestamp.valueOf(returned.atStartOfDay()),key,json.write(Map.of("original",p,"datesSynthetic",true,"demoDate",date.toString())));
            }
            case "theses"->{title=val(p,"学位论文名称");db.jdbc.update("INSERT INTO theses(source_key,student_no,author,title,source_json) VALUES(?,?,?,?,?)",key,val(p,"学号"),val(p,"姓名"),title,raw);}
            case "patents"->{title=val(p,"标题");db.jdbc.update("INSERT INTO patents(source_key,title,application_no,status,source_json) VALUES(?,?,?,?,?)",key,title,val(p,"申请号"),row.sheet(),raw);}
            case "projects"->{title=val(p,"项目名称");db.jdbc.update("INSERT INTO projects(source_key,title,leader,source_json) VALUES(?,?,?,?)",key,title,val(p,"项目负责人"),raw);}
            case "courses"->{title=val(p,"课程名称");db.jdbc.update("INSERT INTO courses(source_key,code,title,teacher,source_json) VALUES(?,?,?,?,?)",key,val(p,"课程编号"),title,val(p,"主讲教师"),raw);}
            case "textbooks"->{title=val(p,"教材名称");db.jdbc.update("INSERT INTO textbooks(source_key,title,author,isbn,source_json) VALUES(?,?,?,?,?)",key,title,val(p,"作者"),val(p,"ISBN"),raw);}
            default->throw new ApiException(400,"未知类型");
        }
        if(Set.of("theses","patents","textbooks","courses","projects").contains(kind)){
            var projection=new LinkedHashMap<String,Object>();projection.put("title",title);projection.put("sourceKey",key);projection.put("sourceFields",p);
            if(kind.equals("patents")){projection.put("category","专利");projection.put("status",row.sheet());}
            if(kind.equals("textbooks"))projection.put("category","教材");
            if(kind.equals("theses")){projection.put("category","科研论文");projection.put("paperKind","学位论文");}
            String type=Set.of("courses","projects").contains(kind)?kind:"achievements";
            content.save(type,null,projection);
        }
    }
}
