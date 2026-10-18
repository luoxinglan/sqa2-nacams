package cn.sqa2.nacams;

import cn.sqa2.nacams.admin.UserService;
import cn.sqa2.nacams.auth.AuthService;
import cn.sqa2.nacams.backup.BackupService;
import cn.sqa2.nacams.common.*;
import cn.sqa2.nacams.portal.MediaService;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:nacams;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.profiles.active=test","app.storage=target/test-storage","app.bootstrap.password=Test-Admin-2026"})
@AutoConfigureMockMvc
class CoreIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired Db db;@Autowired UserService users;
    @Autowired AuthService auth;@Autowired BackupService backup;@Autowired MediaService media;
    String admin;
    @BeforeEach void reset(){
        db.jdbc.update("DELETE FROM auth_sessions");var tables=new ArrayList<>(BackupService.TABLES);Collections.reverse(tables);
        for(String t:tables)db.jdbc.update("DELETE FROM "+t);
        db.jdbc.update("INSERT INTO library_config VALUES(1,2,30,3)");
        users.save(null,new HashMap<>(Map.of("username","admin","password","Test-Admin-2026","name","管理员","identityType","staff","role","admin")));
        admin=token("admin");
    }
    String token(String username){return auth.login(username,"Test-Admin-2026").get("token").toString();}
    Map<String,Object> teacher(String username,String number){return new HashMap<>(Map.of("username",username,"password","Test-Admin-2026","identityType","teacher","name","测试教师","teacherType","full","employeeNo",number));}
    Map<String,Object> student(String username,String number){return new HashMap<>(Map.of("username",username,"password","Test-Admin-2026","identityType","student","name","测试学生","studentNo",number,"status","在读","enrollmentYear",2026));}
    MockHttpServletRequestBuilder with(MockHttpServletRequestBuilder req,String token){return req.header("Authorization","Bearer "+token);}
    JsonNode response(ResultActions result)throws Exception{return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));}
    @Test void authSignoutAndPasswordRevoke()throws Exception{
        long id=Db.id(users.save(null,student("student","3120260001")).get("id"));String t=token("student");
        mvc.perform(with(put("/api/v1/me/password"),t).contentType("application/json").content("{\"old\":\"Test-Admin-2026\",\"new\":\"Changed-Password-2026\"}")).andExpect(status().isOk());
        mvc.perform(with(get("/api/v1/auth/me"),t)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM signin_logs WHERE user_id=? AND signout_at IS NOT NULL",Long.class,id)).isEqualTo(1);
        String again=auth.login("student","Changed-Password-2026").get("token").toString();
        mvc.perform(with(post("/api/v1/auth/logout"),again)).andExpect(status().isOk());
        mvc.perform(with(get("/api/v1/auth/me"),again)).andExpect(status().isUnauthorized());
    }
    @Test void roleEscalationAndLastAdminAreRejected()throws Exception{
        users.save(null,student("student","3120260001"));String t=token("student");
        mvc.perform(with(put("/api/v1/me/profile"),t).contentType("application/json").content("{\"isDirector\":true}")).andExpect(status().isForbidden());
        mvc.perform(with(get("/api/v1/admin/users"),t)).andExpect(status().isForbidden());
        long id=Db.id(db.one("SELECT id FROM users WHERE username='admin'").get("id"));
        mvc.perform(with(delete("/api/v1/admin/users/"+id),admin)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }
    @Test void publicListAndDetailsNeverContainPrivateFields()throws Exception{
        var p=student("student","3120260001");p.put("mobile","13800000000");p.put("birthDate","2001-01-02");p.put("email","test@example.invalid");
        long id=Db.id(users.save(null,p).get("id"));
        for(String path:List.of("/api/v1/students","/api/v1/students/"+id)){
            String text=mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(text).contains("test@example.invalid").doesNotContain("13800000000","2001-01-02","password","profile_json","token_hash","birthDate","mobile");
        }
        mvc.perform(with(get("/api/v1/auth/me"),token("student"))).andExpect(jsonPath("$.data.mobile").value("13800000000"));
    }
    @Test void teacherEncodingExternalAndImportRowTransactions()throws Exception{
        var bad=teacher("bad","123");
        mvc.perform(with(post("/api/v1/admin/users"),admin).contentType("application/json").content(json.writeValueAsBytes(bad))).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.data.fields.employeeNo").exists());
        var external=teacher("external","");external.put("teacherType","external");external.put("appointmentNo","EX-1");external.put("appointmentTerm","2026-2027");users.save(null,external);
        var duplicate=new HashMap<>(external);duplicate.put("username","duplicate");assertThatThrownBy(()->users.save(null,duplicate)).isInstanceOf(ApiException.class);
        String csv="username,password,identityType,name,studentNo,status\nvalid,Test-Admin-2026,student,学生,3120260001,在读\nbad,Test-Admin-2026,student,学生,wrong,在读\nvalid,Test-Admin-2026,student,学生,3120260002,在读\n";
        var file=new MockMultipartFile("file","users.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8));
        mvc.perform(with(multipart("/api/v1/admin/users/import").file(file),admin)).andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(1)).andExpect(jsonPath("$.data.failed").value(2));
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username='bad'",Long.class)).isZero();
    }
    @Test void statisticsOnlyDirectorAndAssignedStudents()throws Exception{
        var p=teacher("teacher","7220100001");long teacher=Db.id(users.save(null,p).get("id"));
        var own=student("own","3120260001");own.put("advisorUserId",teacher);users.save(null,own);
        var other=student("other","3120260002");other.put("advisor","外部教师");users.save(null,other);
        token("own");token("other");String t=token("teacher");
        mvc.perform(with(get("/api/v1/stats/signin?scope=mine"),t)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(with(get("/api/v1/stats/signin?scope=all"),t)).andExpect(status().isForbidden());
        mvc.perform(with(get("/api/v1/stats/signin?scope=all"),admin)).andExpect(status().isForbidden());
        users.save(teacher,Map.of("isDirector",true));
        mvc.perform(with(get("/api/v1/stats/signin?scope=all"),t)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(4));
    }
    @Test void contentCrudKeywordAndPagination()throws Exception{
        var saved=response(mvc.perform(with(post("/api/v1/admin/content/news"),admin).contentType("application/json").content("{\"title\":\"测试动态\",\"category\":\"通知通告\",\"body\":\"网络攻防\"}")).andExpect(status().isOk()));
        long id=saved.at("/data/id").asLong();
        mvc.perform(get("/api/v1/news?keyword=攻防")).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/home/summary")).andExpect(jsonPath("$.data.notificationCount").value(1));
        mvc.perform(get("/api/v1/news?page_size=201")).andExpect(status().isBadRequest());
        mvc.perform(with(put("/api/v1/admin/content/news/"+id),admin).contentType("application/json").content("{\"title\":\"已修改\"}")).andExpect(status().isOk());
        mvc.perform(with(delete("/api/v1/admin/content/news/"+id),admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/news/"+id)).andExpect(status().isNotFound());
    }
    @Test void baselineXlsxBookMappingIsIdempotent()throws Exception{
        byte[] data;
        try(var w=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            var s=w.createSheet("图书信息");var h=s.createRow(0);h.createCell(0).setCellValue("图书编号");h.createCell(1).setCellValue("图书名称");
            var r=s.createRow(1);r.createCell(0).setCellValue("B001");r.createCell(1).setCellValue("数据库测试");w.write(out);data=out.toByteArray();
        }
        var file=new MockMultipartFile("file","books.xlsx","application/octet-stream",data);
        mvc.perform(with(multipart("/api/v1/admin/import/baseline").file(file).param("kind","books").param("purchaseYear","2026"),admin)).andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(1));
        mvc.perform(with(multipart("/api/v1/admin/import/baseline").file(file).param("kind","books"),admin)).andExpect(jsonPath("$.data.skipped").value(1));
        assertThat(db.one("SELECT code FROM books WHERE legacy_code='B001'").get("code")).isEqualTo("2026001");
    }
    @Test void backupRestoreRollbackAndTokenInvalidation()throws Exception{
        users.save(null,student("student","3120260001"));
        var saved=backup.create();byte[] data=Files.readAllBytes(backup.file(saved.get("fileName").toString()));
        users.save(null,student("later","3120260002"));
        var file=new MockMultipartFile("file","backup.json","application/json",data);
        mvc.perform(with(multipart("/api/v1/admin/restore").file(file),admin)).andExpect(status().isConflict());
        mvc.perform(with(multipart("/api/v1/admin/restore").file(file).param("confirm","true"),admin)).andExpect(status().isOk());
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username='later'",Long.class)).isZero();
        mvc.perform(with(get("/api/v1/auth/me"),admin)).andExpect(status().isUnauthorized());
        admin=token("admin");
        var broken=json.readTree(data);((com.fasterxml.jackson.databind.node.ObjectNode)broken.at("/tables/students/0")).put("user_id",999999);
        var bad=new MockMultipartFile("file","backup.json","application/json",json.writeValueAsBytes(broken));
        mvc.perform(with(multipart("/api/v1/admin/restore").file(bad).param("confirm","true"),admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(with(get("/api/v1/auth/me"),admin)).andExpect(status().isOk());
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM students",Long.class)).isEqualTo(1);
    }
    @Test void privateMediaIsProtectedAndRestored()throws Exception{
        byte[] bytes="private handout".getBytes(StandardCharsets.UTF_8);var upload=new MockMultipartFile("file","handout.txt","text/plain",bytes);
        var saved=media.savePrivate(upload);String url=saved.get("url").toString();
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(with(get(url),admin)).andExpect(status().isOk()).andExpect(content().bytes(bytes));
        var snap=backup.create();byte[] backupBytes=Files.readAllBytes(backup.file(snap.get("fileName").toString()));
        backup.restore(new MockMultipartFile("file","snapshot.json","application/json",backupBytes),true);admin=token("admin");
        mvc.perform(with(get(url),admin)).andExpect(status().isOk()).andExpect(content().bytes(bytes));
    }
    @Test void deactivateKeepsHistoryAndRevokesSessions()throws Exception{
        long id=Db.id(users.save(null,student("student","3120260001")).get("id"));String t=token("student");
        mvc.perform(with(delete("/api/v1/admin/users/"+id),admin)).andExpect(status().isOk());
        mvc.perform(with(get("/api/v1/auth/me"),t)).andExpect(status().isUnauthorized());
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM signin_logs WHERE user_id=?",Long.class,id)).isEqualTo(1);
        mvc.perform(get("/api/v1/students/"+id)).andExpect(status().isNotFound());
    }
    @Test void cachedFormulaIsReadWithoutEvaluationAndInvalidNumberIsQuarantined()throws Exception{
        byte[] bytes;
        try(var w=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            var s=w.createSheet("毕业学生");var h=s.createRow(0);
            String[] heads={"年级","学号","姓名","导师","类别"};for(int i=0;i<heads.length;i++)h.createCell(i).setCellValue(heads[i]);
            var a=s.createRow(1);a.createCell(0).setCellFormula("MID(B2,3,4)");a.createCell(1).setCellValue("3120260001");a.createCell(2).setCellValue("测试学生");a.createCell(3).setCellValue("外部导师");a.createCell(4).setCellValue("学术硕士");
            w.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(a.getCell(0));
            var b=s.createRow(2);b.createCell(0).setCellValue(2026);b.createCell(1).setCellValue("3420260001");b.createCell(2).setCellValue("待核学生");
            w.write(out);bytes=out.toByteArray();
        }
        var file=new MockMultipartFile("file","students.xlsx","application/octet-stream",bytes);
        mvc.perform(with(multipart("/api/v1/admin/import/baseline").file(file).param("kind","students").param("initialPassword","Test-Admin-2026"),admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(1)).andExpect(jsonPath("$.data.failed").value(1));
        mvc.perform(with(get("/api/v1/admin/import/rows?status=rejected"),admin)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/admin/import/rows")).andExpect(status().isUnauthorized());
        assertThat(db.jdbc.queryForObject("SELECT enrollment_year FROM students",Integer.class)).isEqualTo(2026);
    }
    @Test void historicalBorrowDatesCannotPredatePurchaseYear()throws Exception{
        users.save(null,student("student","3120260001"));
        db.jdbc.update("INSERT INTO books(code,legacy_code,title,purchase_year) VALUES('2026001','B001','图书',2026)");
        String csv="编号,书名,借阅人\nB001,图书,测试学生\n";var file=new MockMultipartFile("file","borrows.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8));
        mvc.perform(with(multipart("/api/v1/admin/import/baseline").file(file).param("kind","borrows").param("demoDate","2026-01-01"),admin)).andExpect(jsonPath("$.data.failed").value(1));
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM borrows",Long.class)).isZero();
        mvc.perform(with(multipart("/api/v1/admin/import/baseline").file(file).param("kind","borrows").param("demoDate","2026-10-17"),admin)).andExpect(jsonPath("$.data.success").value(1));
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM borrows WHERE returned_at IS NOT NULL AND borrowed_at>=?",Long.class,java.sql.Timestamp.valueOf("2026-01-01 00:00:00"))).isEqualTo(1);
    }

    @Test void nameRuleIsConsistentAcrossSavePrecheckAndImport()throws Exception{
        var bad=teacher("bad-name","6120100001");bad.put("name","张三2");
        mvc.perform(with(post("/api/v1/admin/users"),admin).contentType("application/json").content(json.writeValueAsBytes(bad)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.data.fields.name").value(org.hamcrest.Matchers.containsString("姓名须为汉字")));
        var precheck=Map.of("type","teacher","fields",Map.of("name","张三2","teacherType","full","employeeNo","6120100001"));
        mvc.perform(with(post("/api/v1/validate"),admin).contentType("application/json").content(json.writeValueAsBytes(precheck)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.data.fields.name").value(org.hamcrest.Matchers.containsString("姓名须为汉字")));
        String csv="username,password,identityType,name,studentNo,status\n"
                +"bad,Test-Admin-2026,student,张三2,3120260001,在读\n"
                +"good,Test-Admin-2026,student,张三,3120260002,在读\n";
        var file=new MockMultipartFile("file","users.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8));
        mvc.perform(with(multipart("/api/v1/admin/users/import").file(file),admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(1)).andExpect(jsonPath("$.data.failed").value(1));
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username='bad'",Long.class)).isZero();
        var p=teacher("changer","6120100001");p.put("name","张三");
        long id=Db.id(users.save(null,p).get("id"));
        mvc.perform(with(put("/api/v1/admin/users/"+id),admin).contentType("application/json").content("{\"name\":\"张三2\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.data.fields.name").exists());
        assertThat(db.one("SELECT name FROM users WHERE id=?",id).get("name")).isEqualTo("张三");
    }

    @Test void baselineStudentImportSupportsNameExemptAndKeepsNameVerbatim()throws Exception{
        users.save(null,student("existing","3120260001"));
        byte[] data;
        try(var w=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            var s=w.createSheet("在读学生");
            String[] heads={"年级","学号","姓名","姓名豁免","导师","类别"};
            var h=s.createRow(0);for(int i=0;i<heads.length;i++)h.createCell(i).setCellValue(heads[i]);
            var a=s.createRow(1);a.createCell(0).setCellValue(2026);a.createCell(1).setCellValue("3820260001");a.createCell(2).setCellValue("John Smith");a.createCell(3).setCellValue("是");a.createCell(4).setCellValue("外部导师");a.createCell(5).setCellValue("学术硕士");
            var b=s.createRow(2);b.createCell(0).setCellValue(2026);b.createCell(1).setCellValue("3120260003");b.createCell(2).setCellValue(" 张三 ");b.createCell(3).setCellValue("");b.createCell(4).setCellValue("外部导师");b.createCell(5).setCellValue("学术硕士");
            var c=s.createRow(3);c.createCell(0).setCellValue(2026);c.createCell(1).setCellValue("3120260004");c.createCell(2).setCellValue("李四");c.createCell(3).setCellValue("");c.createCell(4).setCellValue("外部导师");c.createCell(5).setCellValue("学术硕士");
            w.write(out);data=out.toByteArray();
        }
        var file=new MockMultipartFile("file","students.xlsx","application/octet-stream",data);
        mvc.perform(with(multipart("/api/v1/admin/import/baseline").file(file).param("kind","students").param("initialPassword","Test-Admin-2026"),admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(2)).andExpect(jsonPath("$.data.failed").value(1));
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username='3820260001' AND profile_json LIKE '%\"nameExempt\":true%'",Long.class)).isEqualTo(1);
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE name LIKE ' %' OR name LIKE '% '",Long.class)).isZero();
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username='3120260003'",Long.class)).isZero();
    }
}
