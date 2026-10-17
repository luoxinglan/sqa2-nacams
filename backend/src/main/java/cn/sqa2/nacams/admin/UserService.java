package cn.sqa2.nacams.admin;

import cn.sqa2.nacams.auth.*;
import cn.sqa2.nacams.common.*;
import cn.sqa2.nacams.validation.ProfileValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class UserService {
    private static final Set<String> PROFILE = Set.of("name","identityType","employeeNo","teacherType","appointmentNo","appointmentTerm",
            "studentNo","status","enrollmentYear","advisor","advisorEmployeeNo","advisorUserId","advisorUnit","coAdvisor","category",
            "gender","nationality","unit","unitEnglish","englishFamilyName","englishGivenName","title","supervisorQualification",
            "position","birthDate","email","mobile","officePhone","countryCode","remark","remarkEnglish","photo",
            "socialPositions","collegePosition","collegeAcademicPosition","enrollmentDate","graduationDate","firstEmployer","nameExempt");
    private static final Set<String> SELF = Set.of("englishFamilyName","englishGivenName","birthDate","email","mobile","officePhone","countryCode","remark","remarkEnglish");
    private static final Set<String> PUBLIC = Set.of("name","identityType","employeeNo","teacherType","studentNo","status","enrollmentYear",
            "advisor","advisorUnit","coAdvisor","category","gender","nationality","unit","unitEnglish","englishFamilyName","englishGivenName",
            "title","supervisorQualification","position","email","photo","socialPositions","collegePosition","collegeAcademicPosition",
            "enrollmentDate","graduationDate","firstEmployer");
    private final Db db; private final Json json; private final AuthService auth; private final ProfileValidator validator;
    public UserService(Db db, Json json, AuthService auth, ProfileValidator validator) { this.db=db;this.json=json;this.auth=auth;this.validator=validator; }
    public Map<String,Object> get(long id) {
        var row=db.one("SELECT * FROM users WHERE id=?",id);
        var result=json.map(Db.str(row,"profile_json"));
        result.putAll(Map.of("id",id,"username",row.get("username"),"role",row.get("role"),"identityType",row.get("identity_type"),
                "name",row.get("name"),"isDirector",Db.bool(row.get("is_director")),"isLibrarian",Db.bool(row.get("is_librarian")),"active",Db.bool(row.get("active"))));
        return result;
    }
    public Map<String,Object> publicView(long id) {
        var full=get(id);var result=new LinkedHashMap<String,Object>();result.put("id",id);
        for (String key:PUBLIC) if(full.containsKey(key)) result.put(key,full.get(key));
        return result;
    }
    @Transactional
    public Map<String,Object> save(Long id, Map<String,Object> input) {
        Map<String,Object> current=id==null?new LinkedHashMap<>():get(id);
        var p=new LinkedHashMap<String,Object>();
        for(String key:PROFILE) {
            if(current.containsKey(key)) p.put(key,current.get(key));
            if(input.containsKey(key)) p.put(key,input.get(key));
        }
        validator.validate(p);
        String identity=Db.str(p,"identityType");
        if(id!=null && !identity.equals(current.get("identityType"))) throw new ApiException(409,"已有账号不可更换教师/学生身份，请新建账号");
        String username=input.containsKey("username")?Db.str(input,"username"):Db.str(current,"username");
        if(!username.matches("[A-Za-z0-9_.@-]{3,100}")) throw new ApiException(422,"用户名应为3至100位字母、数字或_.@-");
        String role=input.containsKey("role")?Db.str(input,"role"):Objects.toString(current.getOrDefault("role","user"));
        if(!Set.of("admin","user").contains(role)) throw new ApiException(422,"角色仅支持admin/user");
        boolean director=input.containsKey("isDirector")?flag(input,"isDirector"):Db.bool(current.get("isDirector"));
        boolean librarian=input.containsKey("isLibrarian")?flag(input,"isLibrarian"):Db.bool(current.get("isLibrarian"));
        if((director||librarian) && !identity.equals("teacher")) throw new ApiException(422,"所长/图书管理身份必须指定给教师");
        boolean active=input.containsKey("active")?flag(input,"active"):id==null||Db.bool(current.get("active"));
        if(id!=null && "admin".equals(current.get("role")) && (!"admin".equals(role)||!active)) protectLastAdmin(id);
        if("external".equals(Db.str(p,"teacherType"))) {
            var matches=db.jdbc.queryForList("SELECT u.id FROM teachers t JOIN users u ON u.id=t.user_id WHERE u.name=? AND t.appointment_no=? AND u.id<>?",
                    Db.str(p,"name"),Db.str(p,"appointmentNo"),id==null?-1:id);
            if(!matches.isEmpty()) throw new ApiException(409,"姓名与聘书编号重复");
        }
        if(id==null) {
            id=db.insert("INSERT INTO users(username,password_hash,role,identity_type,name,is_director,is_librarian,active,profile_json) VALUES(?,?,?,?,?,?,?,?,?)",
                    username,auth.passwordHash(Db.str(input,"password")),role,identity,Db.str(p,"name"),director,librarian,active,json.write(p));
        } else {
            db.jdbc.update("UPDATE users SET username=?,role=?,name=?,is_director=?,is_librarian=?,active=?,profile_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    username,role,Db.str(p,"name"),director,librarian,active,json.write(p),id);
            if(input.containsKey("resetPassword")) db.jdbc.update("UPDATE users SET password_hash=? WHERE id=?",auth.passwordHash(Db.str(input,"resetPassword")),id);
            if(input.containsKey("resetPassword")||!active||!role.equals(current.get("role")))auth.invalidate(id);
        }
        if(identity.equals("teacher")) {
            if(db.jdbc.queryForObject("SELECT COUNT(*) FROM teachers WHERE user_id=?",Long.class,id)==0)
                db.jdbc.update("INSERT INTO teachers(user_id,teacher_type) VALUES(?,?)",id,Db.str(p,"teacherType"));
            db.jdbc.update("UPDATE teachers SET employee_no=?,teacher_type=?,appointment_no=?,appointment_term=? WHERE user_id=?",
                    nullable(p,"employeeNo"),Db.str(p,"teacherType"),nullable(p,"appointmentNo"),nullable(p,"appointmentTerm"),id);
        } else if(identity.equals("student")) {
            if(db.jdbc.queryForObject("SELECT COUNT(*) FROM students WHERE user_id=?",Long.class,id)==0)
                db.jdbc.update("INSERT INTO students(user_id,student_no,status) VALUES(?,?,?)",id,Db.str(p,"studentNo"),Db.str(p,"status"));
            db.jdbc.update("UPDATE students SET student_no=?,status=?,enrollment_year=?,advisor_name=?,advisor_employee_no=?,advisor_user_id=? WHERE user_id=?",
                    Db.str(p,"studentNo"),Db.str(p,"status"),p.get("enrollmentYear"),nullable(p,"advisor"),nullable(p,"advisorEmployeeNo"),p.get("advisorUserId"),id);
        }
        return get(id);
    }
    private static boolean flag(Map<String,Object> p,String key) {
        Object v=p.get(key);if(v instanceof Boolean b)return b;
        if(v instanceof String s && Set.of("true","false").contains(s))return Boolean.parseBoolean(s);
        throw new ApiException(422,key+"必须是布尔值");
    }
    private Object nullable(Map<String,Object> p,String key) { String v=Db.str(p,key);return v.isBlank()?null:v; }
    private void protectLastAdmin(long id) {
        // 锁定管理员集合，防止并发停用两个管理员后无人可管理。
        var admins=db.jdbc.queryForList("SELECT id FROM users WHERE role='admin' AND active=TRUE FOR UPDATE");
        if(admins.size()<=1)throw new ApiException(409,"不能移除最后一名有效管理员");
    }
    @Transactional public void deactivate(long id) {
        var current=get(id);if("admin".equals(current.get("role")))protectLastAdmin(id);
        db.jdbc.update("UPDATE users SET active=FALSE,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);auth.invalidate(id);
    }
    @Transactional public Map<String,Object> updateSelf(long id,Map<String,Object> input) {
        for(String key:input.keySet())if(!SELF.contains(key))throw new ApiException(403,"本人不能修改字段："+key);
        return save(id,input);
    }
    @Transactional public Map<String,Object> updatePhoto(long id,String url) {
        var p=get(id);p.keySet().retainAll(PROFILE);p.put("photo",url);validator.validate(p);
        db.jdbc.update("UPDATE users SET profile_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",json.write(p),id);return get(id);
    }
    public Map<String,Object> list(String keyword,String role,int page,int size) {
        var args=new ArrayList<Object>();String where="FROM users WHERE 1=1";
        if(!keyword.isBlank()){where+=" AND (name LIKE ? OR username LIKE ?)";args.add("%"+keyword+"%");args.add("%"+keyword+"%");}
        if(!role.isBlank()){where+=" AND role=?";args.add(role);}
        var result=db.page(where,args,"id DESC",page,size);
        @SuppressWarnings("unchecked") var items=(List<Map<String,Object>>)result.get("items");
        result.put("items",items.stream().map(row->get(Db.id(row.get("id")))).toList());return result;
    }
}
