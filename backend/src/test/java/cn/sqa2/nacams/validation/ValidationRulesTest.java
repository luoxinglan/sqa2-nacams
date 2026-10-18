package cn.sqa2.nacams.validation;

import cn.sqa2.nacams.common.ApiException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ValidationRulesTest {
    private final FullProfileValidator validator = new FullProfileValidator(2, 6);

    private Map<String, Object> teacher(String type, String employeeNo) {
        var p = new LinkedHashMap<String, Object>();
        p.put("identityType", "teacher");
        p.put("name", "测试教师");
        p.put("teacherType", type);
        p.put("employeeNo", employeeNo);
        return p;
    }

    private Map<String, Object> student(String number) {
        var p = new LinkedHashMap<String, Object>();
        p.put("identityType", "student");
        p.put("name", "测试学生");
        p.put("studentNo", number);
        p.put("status", "在读");
        return p;
    }

    private Map<String, Object> named(String name) {
        var p = teacher("full", "6120100001");
        p.put("name", name);
        return p;
    }

    private void assertField(Map<String, Object> fields, String key) {
        var error = catchThrowable(() -> validator.validate(fields));
        assertThat(error).isInstanceOf(ApiException.class);
        assertThat(((ApiException) error).code).isEqualTo(422);
        @SuppressWarnings("unchecked")
        var details = (Map<String, Object>) ((ApiException) error).data;
        @SuppressWarnings("unchecked")
        var messages = (Map<String, String>) details.get("fields");
        assertThat(messages).containsKey(key);
    }

    /** 取出姓名错误文案，用于区分字符集/结构/长度三类失败。 */
    private String nameMessage(Map<String, Object> fields) {
        var error = catchThrowable(() -> validator.validate(fields));
        assertThat(error).isInstanceOf(ApiException.class);
        assertThat(((ApiException) error).code).isEqualTo(422);
        @SuppressWarnings("unchecked")
        var details = (Map<String, Object>) ((ApiException) error).data;
        @SuppressWarnings("unchecked")
        var messages = (Map<String, String>) details.get("fields");
        assertThat(messages).containsKey("name");
        return messages.get("name");
    }

    @Test
    void fullTeacherEmployeeNumberAcceptsThreePrefixes() {
        for (String prefix : List.of("61201", "72201", "74201")) {
            assertThatCode(() -> validator.validate(teacher("full", prefix + "00001"))).doesNotThrowAnyException();
        }
        assertField(teacher("full", "123"), "employeeNo");
        assertField(teacher("full", "7520200001"), "employeeNo");
        assertField(teacher("full", "612010000A"), "employeeNo");
    }

    @Test
    void teacherTypeOnlySupportsFullAndExternal() {
        assertField(teacher("校内兼职", "6120100001"), "teacherType");
        var external = teacher("external", "");
        external.put("appointmentNo", "EX-2026");
        external.put("appointmentTerm", "2026-2027");
        assertThatCode(() -> validator.validate(external)).doesNotThrowAnyException();
        assertField(teacher("external", "6120100001"), "employeeNo");
        var missing = teacher("external", "");
        assertField(missing, "appointmentNo");
        missing.put("appointmentNo", "EX-2026");
        assertField(missing, "appointmentTerm");
    }

    @Test
    void studentNumberEncodesLevelCategoryYearAndSequence() {
        assertThatCode(() -> validator.validate(student("3120260001"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(student("1820260001"))).doesNotThrowAnyException();
        assertField(student("1420260001"), "studentNo");
        assertField(student("312026001"), "studentNo");
        assertField(student("3420260001"), "studentNo");
        assertField(student("312026000A"), "studentNo");
    }

    @Test
    void chineseNameRejectsLatinDigitsAndOutOfRangeLength() {
        assertField(named("Zhang San"), "name");
        assertField(named("张三2"), "name");
        assertField(named("张"), "name");
        assertField(named("张".repeat(16)), "name");
        assertField(named("欧阳娜娜娜娜娜"), "name");
        // 含间隔号的少数民族汉字译写姓名不受 6 字上限，保持既有正例。
        assertThatCode(() -> validator.validate(named("阿依古丽·买买提"))).doesNotThrowAnyException();
    }

    @Test
    void nameExemptionOnlyAppliesToInternationalStudentsWithFlag() {
        var international = student("3820260001");
        international.put("name", "John Smith");
        international.put("nameExempt", true);
        assertThatCode(() -> validator.validate(international)).doesNotThrowAnyException();

        var notInternational = student("3120260001");
        notInternational.put("name", "John Smith");
        notInternational.put("nameExempt", true);
        assertField(notInternational, "name");

        var teacher = teacher("full", "6120100001");
        teacher.put("name", "John Smith");
        teacher.put("nameExempt", true);
        assertField(teacher, "name");
    }

    @Test
    void contactFieldsFollowCampusRules() {
        var p = student("3120260001");
        p.put("mobile", "13800000000");
        p.put("officePhone", "01068910000");
        p.put("email", "student@example.invalid");
        p.put("birthDate", "2001-01-02");
        assertThatCode(() -> validator.validate(p)).doesNotThrowAnyException();

        var international = student("3820260001");
        international.put("mobile", "+8613800000000");
        assertThatCode(() -> validator.validate(international)).doesNotThrowAnyException();

        var wrongMobile = student("3120260001");
        wrongMobile.put("mobile", "1380000000");
        assertField(wrongMobile, "mobile");

        var wrongOffice = student("3120260001");
        wrongOffice.put("officePhone", "01068920000");
        assertField(wrongOffice, "officePhone");

        var wrongEmail = student("3120260001");
        wrongEmail.put("email", "not-an-email");
        assertField(wrongEmail, "email");

        var wrongBirth = student("3120260001");
        wrongBirth.put("birthDate", "2001/01/02");
        assertField(wrongBirth, "birthDate");
    }

    @Test
    void errorsOnlyContainSubmittedIllegalFields() {
        var p = student("3120260001");
        p.put("email", "bad");
        var error = catchThrowable(() -> validator.validate(p));
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) ((ApiException) error).data;
        @SuppressWarnings("unchecked")
        var fields = (Map<String, String>) data.get("fields");
        assertThat(fields).containsOnlyKeys("email");
    }

    @Test
    void nameCharacterSetRejectsNonBasicHanCharacters() {
        // 〇、CJK 扩展 A、扩展 B 均不在 U+4E00～U+9FA5 基本区，一律拒绝。
        for (String name : List.of("〇〇", "李㛃华", "𠀀三", "张三２", "张三2", "张三😀", "张三！",
                "买买提•阿不都", "买买提‧阿不都", "买买提・阿不都", "ئالىم", "བཀྲ་ཤིས")) {
            assertThat(nameMessage(named(name))).contains("姓名须为汉字");
        }
        // 繁体、异体字落在基本区，放行（无权威字表，属已知边界）。
        assertThatCode(() -> validator.validate(named("張三"))).doesNotThrowAnyException();
    }

    @Test
    void nameRejectsZeroWidthAndInvisibleCharacters() {
        assertThat(nameMessage(named("张\u200B三"))).contains("姓名须为汉字");
        assertThat(nameMessage(named("张\u00A0三"))).contains("姓名须为汉字");
        assertThat(nameMessage(named("张\uFEFF三"))).contains("姓名须为汉字");
        assertThat(nameMessage(named("张 三"))).contains("姓名须为汉字");
        assertThat(nameMessage(named(" 张三"))).contains("姓名须为汉字");
        assertThat(nameMessage(named("张三 "))).contains("姓名须为汉字");
    }

    @Test
    void nameSeparatorStructureIsEnforced() {
        for (String name : List.of("·买买提", "买买提·", "买买提··阿不都", "张三·", "·")) {
            assertThat(nameMessage(named(name))).contains("间隔号");
        }
        for (String name : List.of("张·三", "张·三·李", "买买提·阿不都", "艾力·买买提·江")) {
            assertThatCode(() -> validator.validate(named(name))).doesNotThrowAnyException();
        }
        // 字符集优先于结构：右侧不是汉字时仍报字符集错误。
        assertThat(nameMessage(named("买买提·A"))).contains("姓名须为汉字");
    }

    @Test
    void nameLengthCountsHanOnlyAndTransliterationIsExemptFromUpperBound() {
        assertThatCode(() -> validator.validate(named("张三"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(named("爱新觉罗玄烨"))).doesNotThrowAnyException();
        assertThat(nameMessage(named("张"))).contains("2至6");
        assertThat(nameMessage(named("欧阳娜娜娜娜娜"))).contains("2至6");
        // 间隔号不计入长度：6 个汉字＋任意个合法「·」均通过。
        assertThatCode(() -> validator.validate(named("买买提·阿不都"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(named("艾力·买买提·江"))).doesNotThrowAnyException();
        // 译写姓名（含「·」）不受 6 字上限，7 个汉字仍通过。
        assertThatCode(() -> validator.validate(named("阿依古丽·买买提"))).doesNotThrowAnyException();
        // 译写姓名仍须至少 2 个汉字。
        assertThat(nameMessage(named("·李"))).contains("间隔号");
    }

    @Test
    void nameLengthConfigurationCanOnlyTighten() {
        // 旧部署值 15 被钳制为 6：7 个字拒绝、6 个字通过。
        var legacy = new FullProfileValidator(2, 15);
        assertThatCode(() -> legacy.validate(named("爱新觉罗玄烨"))).doesNotThrowAnyException();
        assertThat(nameMessage(legacy, named("欧阳娜娜娜娜娜"))).contains("2至6");
        // 下限不得低于 2。
        var looseMin = new FullProfileValidator(1, 6);
        assertThat(nameMessage(looseMin, named("张"))).contains("2至6");
        // 真实收紧生效：3～5 通过，2 与 6 拒绝。
        var tightened = new FullProfileValidator(3, 5);
        assertThatCode(() -> tightened.validate(named("欧阳修文"))).doesNotThrowAnyException();
        assertThatCode(() -> tightened.validate(named("欧阳修文治"))).doesNotThrowAnyException();
        assertField(tightened, named("张三"), "name");
        assertField(tightened, named("爱新觉罗玄烨"), "name");
        // 非法组合回退法定区间 2～6。
        for (var illegal : List.of(new FullProfileValidator(7, 6), new FullProfileValidator(0, 0), new FullProfileValidator(3, 0))) {
            assertThatCode(() -> illegal.validate(named("张三"))).doesNotThrowAnyException();
            assertField(illegal, named("张"), "name");
            assertField(illegal, named("欧阳娜娜娜娜娜"), "name");
        }
    }

    private String nameMessage(FullProfileValidator target, Map<String, Object> fields) {
        var error = catchThrowable(() -> target.validate(fields));
        assertThat(error).isInstanceOf(ApiException.class);
        @SuppressWarnings("unchecked")
        var details = (Map<String, Object>) ((ApiException) error).data;
        @SuppressWarnings("unchecked")
        var messages = (Map<String, String>) details.get("fields");
        assertThat(messages).containsKey("name");
        return messages.get("name");
    }

    private void assertField(FullProfileValidator target, Map<String, Object> fields, String key) {
        var error = catchThrowable(() -> target.validate(fields));
        assertThat(error).isInstanceOf(ApiException.class);
        @SuppressWarnings("unchecked")
        var details = (Map<String, Object>) ((ApiException) error).data;
        @SuppressWarnings("unchecked")
        var messages = (Map<String, String>) details.get("fields");
        assertThat(messages).containsKey(key);
    }

    @Test
    void nameMessagesAreDistinctAndDoNotLeakConfiguration() {
        String charset = nameMessage(named("张三2"));
        String separator = nameMessage(named("·张三"));
        String length = nameMessage(named("张"));
        assertThat(Set.of(charset, separator, length)).hasSize(3);
        assertThat(charset).contains("联系管理员");
        assertThat(separator).contains("间隔号");
        assertThat(length).contains("2至6").contains("不计入").contains("译写姓名不受6字上限");
        for (String message : List.of(charset, separator, length)) {
            assertThat(message).doesNotContain("15").doesNotContain("name-max-length").doesNotContain("app.validation");
        }
    }

    @Test
    void nameExemptionSkipsCharsetStructureAndLengthOnly() {
        var longLatin = student("3820260001");
        longLatin.put("name", "John Smith");
        longLatin.put("nameExempt", true);
        assertThatCode(() -> validator.validate(longLatin)).doesNotThrowAnyException();

        var transliterated = student("3820260001");
        transliterated.put("name", "阿依古丽·买买提");
        transliterated.put("nameExempt", true);
        assertThatCode(() -> validator.validate(transliterated)).doesNotThrowAnyException();

        var blank = student("3820260001");
        blank.put("name", "   ");
        blank.put("nameExempt", true);
        assertThat(nameMessage(blank)).contains("姓名必填");

        var wrongFlagType = student("3820260001");
        wrongFlagType.put("name", "John Smith");
        wrongFlagType.put("nameExempt", "false");
        assertThat(nameMessage(wrongFlagType)).contains("姓名须为汉字");

        var ninthDigit = student("382026000");
        ninthDigit.put("name", "John Smith");
        ninthDigit.put("nameExempt", true);
        assertField(ninthDigit, "name");

        var wrongCategory = student("3220260001");
        wrongCategory.put("name", "John Smith");
        wrongCategory.put("nameExempt", true);
        assertThat(nameMessage(wrongCategory)).contains("姓名须为汉字");
    }

    @Test
    void nameStorageLimitIsOneHundredCodePoints() {
        var exemptHundred = student("3820260001");
        exemptHundred.put("name", "A".repeat(100));
        exemptHundred.put("nameExempt", true);
        assertThatCode(() -> validator.validate(exemptHundred)).doesNotThrowAnyException();

        var exemptOverflow = student("3820260001");
        exemptOverflow.put("name", "A".repeat(101));
        exemptOverflow.put("nameExempt", true);
        assertThat(nameMessage(exemptOverflow)).contains("不超过100字符");

        var normalOverflow = named("张".repeat(101));
        assertThat(nameMessage(normalOverflow)).contains("不超过100字符");

        // 100 个汉字未超存储上限，但常规姓名仍受 2～6 限制。
        assertThat(nameMessage(named("张".repeat(100)))).contains("2至6");
    }
}