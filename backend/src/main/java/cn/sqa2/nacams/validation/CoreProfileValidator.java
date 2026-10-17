package cn.sqa2.nacams.validation;

import cn.sqa2.nacams.common.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import java.util.*;

/** 框架级必填/编码检查；姓名条例的完整语义校验由陈彬的实现补充。 */
@Component
@ConditionalOnMissingBean(value=ProfileValidator.class, ignored=CoreProfileValidator.class)
public class CoreProfileValidator implements ProfileValidator {
    @Override public void validate(Map<String,Object> p) {
        var errors = new LinkedHashMap<String,String>();
        String identity = Db.str(p,"identityType");
        if (!Set.of("teacher","student","staff").contains(identity)) errors.put("identityType","teacher/student/staff 三选一");
        if (Db.str(p,"name").isBlank() || Db.str(p,"name").length()>100) errors.put("name","姓名必填且不超过100字符");
        if ("teacher".equals(identity)) {
            String type = Db.str(p,"teacherType");
            if (!Set.of("full","external").contains(type)) errors.put("teacherType","仅支持full/external");
            if ("full".equals(type) && !Db.str(p,"employeeNo").matches("(?:61201|72201|74201)\\d{5}")) errors.put("employeeNo","全职工号需为指定前缀的10位数字");
            if ("external".equals(type)) {
                if (!Db.str(p,"employeeNo").isBlank()) errors.put("employeeNo","校外兼职无工号");
                if (Db.str(p,"appointmentNo").isBlank()) errors.put("appointmentNo","聘书编号必填");
                if (Db.str(p,"appointmentTerm").isBlank()) errors.put("appointmentTerm","聘期必填");
            }
        }
        if ("student".equals(identity)) {
            if (!Db.str(p,"studentNo").matches("[13][1238]\\d{8}")) errors.put("studentNo","学号需满足10位层次/类别/年份/顺序编码");
            if (!Set.of("在读","毕业","结业肄业").contains(Db.str(p,"status"))) errors.put("status","学生状态不合法");
        }
        if (!Db.str(p,"email").isBlank() && !Db.str(p,"email").matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) errors.put("email","邮箱格式不正确");
        if (!Db.str(p,"birthDate").isBlank()) {
            try { java.time.LocalDate.parse(Db.str(p,"birthDate")); }
            catch (Exception e) { errors.put("birthDate","出生日期格式为YYYY-MM-DD"); }
        }
        if (!errors.isEmpty()) throw new ApiException(422,"字段校验失败",Map.of("fields",errors));
    }
}
