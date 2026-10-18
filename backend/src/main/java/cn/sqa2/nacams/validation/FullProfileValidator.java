package cn.sqa2.nacams.validation;

import cn.sqa2.nacams.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 数据校验引擎（B-01～B-06）完整实现。
 * <p>保存与批量导入均经 {@code UserService.save()} → {@code ProfileValidator.validate()} 调用，
 * 故本实现即唯一权威；{@code @Primary} 使框架级 {@code CoreProfileValidator} 让位，但两者行为兼容。</p>
 */
@Primary
@Component
public class FullProfileValidator implements ProfileValidator {
    /** B-02 全职工号：10 位，前缀 61201（事业编）/72201（校聘）/74201（院聘），三者均为全职。 */
    private static final Pattern FULL_EMPLOYEE = Pattern.compile("(?:61201|72201|74201)\\d{5}");
    /** B-03 学号：首位层次 1/3，次位类别 1/2/3/8，其后 8 位（年份 4 + 顺序 4）。 */
    private static final Pattern STUDENT_NO = Pattern.compile("[13][1238]\\d{8}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    /** B-06 移动电话：11 位数字，可选国家代码前缀（支持外籍人员）。 */
    private static final Pattern MOBILE = Pattern.compile("(?:\\+\\d{1,4})?\\d{11}");
    /** B-06 办公电话：3 位区号 + 8 位号码，号码前 4 位为中关村校区 6891/6894。 */
    private static final Pattern OFFICE_PHONE = Pattern.compile("\\d{3}(?:6891|6894)\\d{4}");
    /**
     * B-04 中文姓名：仅汉字，少数民族姓名允许间隔号「·」。
     * ⚠️ 附件《姓名登记条例（初稿）》PDF 无文字层，长度上下限暂按可配置区间，待人工转录后固化（tasks 2.7）。
     */
    private static final Pattern CHINESE_NAME = Pattern.compile("[\\u4e00-\\u9fa5·]+");
    private final int nameMin;
    private final int nameMax;

    public FullProfileValidator(@Value("${app.validation.name-min-length:2}") int nameMin,
                                @Value("${app.validation.name-max-length:15}") int nameMax) {
        this.nameMin = nameMin;
        this.nameMax = nameMax;
    }

    @Override
    public void validate(Map<String, Object> p) {
        var errors = new LinkedHashMap<String, String>();
        String identity = Db.str(p, "identityType");
        if (!Set.of("teacher", "student", "staff").contains(identity)) errors.put("identityType", "teacher/student/staff 三选一");

        // B-04 中文姓名（留学生豁免）
        String name = Db.str(p, "name");
        if (name.isBlank() || name.length() > 100) {
            errors.put("name", "姓名必填且不超过100字符");
        } else if (!isNameExempt(identity, p)) {
            if (!CHINESE_NAME.matcher(name).matches()) errors.put("name", "姓名须为汉字（留学生可设豁免标记）");
            else if (name.length() < nameMin || name.length() > nameMax) errors.put("name", "姓名长度须为" + nameMin + "至" + nameMax + "个字符");
        }

        if ("teacher".equals(identity)) validateTeacher(p, errors);
        if ("student".equals(identity)) validateStudent(p, errors);

        // B-06 电话 + 通用字段
        String email = Db.str(p, "email");
        if (!email.isBlank() && !EMAIL.matcher(email).matches()) errors.put("email", "邮箱格式不正确");
        String birth = Db.str(p, "birthDate");
        if (!birth.isBlank()) {
            try { LocalDate.parse(birth); } catch (Exception e) { errors.put("birthDate", "出生日期格式为YYYY-MM-DD"); }
        }
        String mobile = Db.str(p, "mobile");
        if (!mobile.isBlank() && !MOBILE.matcher(mobile).matches()) errors.put("mobile", "移动电话须为11位数字，可带国家代码前缀");
        String office = Db.str(p, "officePhone");
        if (!office.isBlank() && !OFFICE_PHONE.matcher(office).matches()) errors.put("officePhone", "办公电话须为3位区号+8位号码（中关村校区以6891/6894开头）");

        if (!errors.isEmpty()) throw new ApiException(422, "字段校验失败", Map.of("fields", errors));
    }

    private void validateTeacher(Map<String, Object> p, Map<String, String> errors) {
        String type = Db.str(p, "teacherType");
        if (!Set.of("full", "external").contains(type)) { errors.put("teacherType", "仅支持full/external"); return; }
        if ("full".equals(type)) {
            if (!FULL_EMPLOYEE.matcher(Db.str(p, "employeeNo")).matches()) errors.put("employeeNo", "全职工号需为61201/72201/74201前缀的10位数字");
        } else {
            if (!Db.str(p, "employeeNo").isBlank()) errors.put("employeeNo", "校外兼职无工号");
            if (Db.str(p, "appointmentNo").isBlank()) errors.put("appointmentNo", "聘书编号必填");
            if (Db.str(p, "appointmentTerm").isBlank()) errors.put("appointmentTerm", "聘期必填");
        }
    }

    private void validateStudent(Map<String, Object> p, Map<String, String> errors) {
        if (!STUDENT_NO.matcher(Db.str(p, "studentNo")).matches()) errors.put("studentNo", "学号需满足10位层次/类别/年份/顺序编码（首位1或3，次位1/2/3/8）");
        if (!Set.of("在读", "毕业", "结业肄业").contains(Db.str(p, "status"))) errors.put("status", "学生状态不合法");
    }

    /** 仅留学生（学号第 2 位 = 8）且显式 nameExempt=true 时豁免常规姓名规则；非留学生误设豁免不生效。 */
    static boolean isNameExempt(String identity, Map<String, Object> p) {
        if (!"student".equals(identity)) return false;
        boolean exempt = p.get("nameExempt") instanceof Boolean b ? b : "true".equalsIgnoreCase(Db.str(p, "nameExempt"));
        if (!exempt) return false;
        String no = Db.str(p, "studentNo");
        return no.length() == 10 && no.charAt(1) == '8';
    }
}