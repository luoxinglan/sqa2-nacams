package cn.sqa2.nacams.validation;

import cn.sqa2.nacams.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>B-04 姓名依据《姓名登记条例（初稿）》第十四条：常规姓名 2～6 个汉字；第四十条"以上/以下"含本数，
 * 故为闭区间 [2,6]。间隔号「·」允许但不计入长度；含「·」的少数民族汉字译写姓名不受 6 字上限，
 * 仅要求不少于 2 个汉字。字符集仅放行基本区汉字 U+4E00～U+9FA5，`〇` 与 CJK 扩展 A/B 均不放行。</p>
 */
@Primary
@Component
public class FullProfileValidator implements ProfileValidator {
    private static final Logger log = LoggerFactory.getLogger(FullProfileValidator.class);

    /** B-02 全职工号：10 位，前缀 61201（事业编）/72201（校聘）/74201（院聘），三者均为全职。 */
    private static final Pattern FULL_EMPLOYEE = Pattern.compile("(?:61201|72201|74201)\\d{5}");
    /** B-03 学号：首位层次 1/3，次位类别 1/2/3/8，其后 8 位（年份 4 + 顺序 4）。 */
    private static final Pattern STUDENT_NO = Pattern.compile("[13][1238]\\d{8}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    /** B-06 移动电话：11 位数字，可选国家代码前缀（支持外籍人员）。 */
    private static final Pattern MOBILE = Pattern.compile("(?:\\+\\d{1,4})?\\d{11}");
    /** B-06 办公电话：3 位区号 + 8 位号码，号码前 4 位为中关村校区 6891/6894。 */
    private static final Pattern OFFICE_PHONE = Pattern.compile("\\d{3}(?:6891|6894)\\d{4}");

    /** B-04 法定姓名长度（条例第十四条），闭区间，代码内固化，配置只能收紧。 */
    private static final int LEGAL_NAME_MIN = 2;
    private static final int LEGAL_NAME_MAX = 6;
    /** 姓名字段存储上限，与 users.name VARCHAR(100) 对齐，按 Unicode 码点计。 */
    private static final int NAME_STORE_MAX = 100;
    /** 姓名允许的间隔号，仅 U+00B7（其他变体 U+2022/U+2027/U+30FB 不放行）。 */
    private static final char NAME_SEPARATOR = '\u00B7';
    private static final int HAN_BEGIN = 0x4E00;
    private static final int HAN_END = 0x9FA5;

    private static final String NAME_REQUIRED_MESSAGE = "姓名必填且不超过100字符";
    private static final String NAME_CHARSET_MESSAGE =
            "姓名须为汉字（间隔号仅支持「·」）；如为少数民族文字姓名或留学生，请联系管理员确认登记方式";
    private static final String NAME_SEPARATOR_MESSAGE = "间隔号「·」不得位于姓名首尾或连续出现，且两侧须为汉字";
    private static final String NAME_LENGTH_MESSAGE =
            "姓名长度须为2至6个汉字（间隔号「·」不计入长度；少数民族译写姓名不受6字上限）";

    private final int nameMin;
    private final int nameMax;

    public FullProfileValidator(@Value("${app.validation.name-min-length:2}") int nameMin,
                                @Value("${app.validation.name-max-length:6}") int nameMax) {
        int min = Math.max(LEGAL_NAME_MIN, nameMin);
        int max = Math.min(LEGAL_NAME_MAX, nameMax);
        if (min > max) {
            log.warn("姓名长度配置非法（配置 min={}、max={}），回退法定区间 {}-{}", nameMin, nameMax, LEGAL_NAME_MIN, LEGAL_NAME_MAX);
            min = LEGAL_NAME_MIN;
            max = LEGAL_NAME_MAX;
        } else if (min != nameMin || max != nameMax) {
            log.warn("姓名长度配置超出法定区间（配置 {}-{}，生效 {}-{}），配置只可收紧不可放宽", nameMin, nameMax, min, max);
        }
        this.nameMin = min;
        this.nameMax = max;
    }

    @Override
    public void validate(Map<String, Object> p) {
        var errors = new LinkedHashMap<String, String>();
        String identity = Db.str(p, "identityType");
        if (!Set.of("teacher", "student", "staff").contains(identity)) errors.put("identityType", "teacher/student/staff 三选一");

        // B-04 中文姓名（留学生豁免）：必填/存储上限 → 豁免 → 字符集 → 间隔号结构 → 长度
        String nameError = validateName(p, identity);
        if (nameError != null) errors.put("name", nameError);

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

    /**
     * 姓名校验，返回首条错误文案；通过返回 {@code null}。
     * <p>姓名取值绕过 {@link Db#str}，保留原文（首尾空白不被静默去除），以拒绝而非改写来满足"禁止静默改写"。</p>
     */
    private String validateName(Map<String, Object> p, String identity) {
        Object raw = p.get("name");
        String name = raw == null ? "" : raw.toString();
        if (name.isBlank() || name.codePointCount(0, name.length()) > NAME_STORE_MAX) {
            return NAME_REQUIRED_MESSAGE;
        }
        if (isNameExempt(identity, p)) return null;
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            if (!isAllowedNameCodePoint(cp)) return NAME_CHARSET_MESSAGE;
            i += Character.charCount(cp);
        }
        if (!hasValidSeparatorStructure(name)) return NAME_SEPARATOR_MESSAGE;
        int length = nameLength(name);
        if (length < nameMin) return NAME_LENGTH_MESSAGE;
        if (!isTransliterated(name) && length > nameMax) return NAME_LENGTH_MESSAGE;
        return null;
    }

    /** 仅放行基本区汉字与间隔号「·」；`〇`(U+3007)、CJK 扩展 A/B 及一切字母数字空白均不放行。 */
    private static boolean isAllowedNameCodePoint(int cp) {
        return cp == NAME_SEPARATOR || (cp >= HAN_BEGIN && cp <= HAN_END);
    }

    /** 间隔号不得位于首尾、不得连续出现，且两侧须为汉字。 */
    private static boolean hasValidSeparatorStructure(String name) {
        int[] codePoints = name.codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            if (codePoints[i] != NAME_SEPARATOR) continue;
            if (i == 0 || i == codePoints.length - 1) return false;
            int left = codePoints[i - 1];
            int right = codePoints[i + 1];
            if (!isHan(left) || !isHan(right)) return false;
        }
        return true;
    }

    private static boolean isHan(int cp) {
        return cp >= HAN_BEGIN && cp <= HAN_END;
    }

    /** 汉字个数，间隔号不计入。 */
    private static int nameLength(String name) {
        int count = 0;
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            if (cp != NAME_SEPARATOR) count++;
            i += Character.charCount(cp);
        }
        return count;
    }

    /** 含「·」即按少数民族汉字译写姓名处理（系统无民族字段，采用可判定特征的工程近似）。 */
    private static boolean isTransliterated(String name) {
        return name.indexOf(NAME_SEPARATOR) >= 0;
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