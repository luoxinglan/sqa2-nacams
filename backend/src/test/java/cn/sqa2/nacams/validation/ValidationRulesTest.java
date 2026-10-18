package cn.sqa2.nacams.validation;

import cn.sqa2.nacams.common.ApiException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ValidationRulesTest {
    private final FullProfileValidator validator = new FullProfileValidator(2, 15);

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

        var latin = teacher("full", "6120100001");
        latin.put("name", "Zhang San");
        assertField(latin, "name");
        var digit = teacher("full", "6120100001");
        digit.put("name", "张三2");
        assertField(digit, "name");
        var tooShort = teacher("full", "6120100001");
        tooShort.put("name", "张");
        assertField(tooShort, "name");
        var longName = teacher("full", "6120100001");
        longName.put("name", "张".repeat(16));
        assertField(longName, "name");
        var ethnic = teacher("full", "6120100001");
        ethnic.put("name", "阿依古丽·买买提");
        assertThatCode(() -> validator.validate(ethnic)).doesNotThrowAnyException();
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
}