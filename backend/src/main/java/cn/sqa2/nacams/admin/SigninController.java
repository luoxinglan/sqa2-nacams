package cn.sqa2.nacams.admin;

import cn.sqa2.nacams.auth.*;
import cn.sqa2.nacams.common.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
@RestController
@RequestMapping("/api/v1/stats")
public class SigninController {
    private final Access access;private final Db db;
    public SigninController(Access access,Db db){this.access=access;this.db=db;}
    @GetMapping("/signin")public Api<?> signin(@RequestParam(defaultValue="mine")String scope,
            @RequestParam(defaultValue="1")int page,@RequestParam(name="page_size",defaultValue="20")int size){
        var current=access.user();var args=new ArrayList<Object>();String from="FROM signin_logs WHERE 1=1";
        if(scope.equals("all")){if(!current.isDirector())throw new ApiException(403,"仅所长可查看全员统计");}
        else if(scope.equals("mine")){
            var teacher=db.jdbc.queryForList("SELECT employee_no FROM teachers WHERE user_id=? AND teacher_type='full'",current.id());
            if(teacher.isEmpty())throw new ApiException(403,"仅全职教师可查看名下学生统计");
            // 有关联ID/工号时优先使用；仅原始数据缺失关联字段时按姓名兜底。
            from+=" AND user_id IN (SELECT user_id FROM students WHERE advisor_user_id=? OR (advisor_user_id IS NULL AND advisor_employee_no=?) OR (advisor_user_id IS NULL AND advisor_employee_no IS NULL AND advisor_name=?))";
            args.add(current.id());args.add(teacher.get(0).get("employee_no"));args.add(current.name());
        }else throw new ApiException(400,"scope为all或mine");
        var result=db.page(from,args,"signin_at DESC,id DESC",page,size);
        @SuppressWarnings("unchecked")var rows=(List<Map<String,Object>>)result.get("items");
        result.put("items",rows.stream().map(row->{var p=new LinkedHashMap<String,Object>();p.put("id",row.get("id"));p.put("userId",row.get("user_id"));
            p.put("name",db.one("SELECT name FROM users WHERE id=?",row.get("user_id")).get("name"));
            p.put("signinAt",row.get("signin_at"));p.put("signoutAt",row.get("signout_at"));return p;}).toList());return Api.ok(result);
    }
}
