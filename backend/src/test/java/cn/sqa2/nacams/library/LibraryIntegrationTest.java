package cn.sqa2.nacams.library;

import cn.sqa2.nacams.admin.UserService;
import cn.sqa2.nacams.auth.AuthService;
import cn.sqa2.nacams.backup.BackupService;
import cn.sqa2.nacams.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:nacams;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.profiles.active=test", "app.storage=target/test-storage", "app.bootstrap.password=Test-Admin-2026"})
@AutoConfigureMockMvc
class LibraryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Db db;
    @Autowired UserService users;
    @Autowired AuthService auth;
    @Autowired LibraryScheduler scheduler;
    String admin;

    @BeforeEach
    void reset() {
        db.jdbc.update("DELETE FROM auth_sessions");
        var tables = new ArrayList<>(BackupService.TABLES);
        Collections.reverse(tables);
        for (String table : tables) db.jdbc.update("DELETE FROM " + table);
        db.jdbc.update("INSERT INTO library_config VALUES(1,2,30,3)");
        users.save(null, new HashMap<>(Map.of("username", "admin", "password", "Test-Admin-2026",
                "name", "管理员", "identityType", "staff", "role", "admin")));
        admin = token("admin");
    }

    String token(String username) { return auth.login(username, "Test-Admin-2026").get("token").toString(); }

    long student(String username, String number) {
        return Db.id(users.save(null, new HashMap<>(Map.of("username", username, "password", "Test-Admin-2026",
                "identityType", "student", "name", "测试学生", "studentNo", number, "status", "在读"))).get("id"));
    }

    String librarian(String username, String employeeNo) {
        var profile = new HashMap<String, Object>(Map.of("username", username, "password", "Test-Admin-2026",
                "identityType", "teacher", "name", "图书教师", "teacherType", "full", "employeeNo", employeeNo));
        profile.put("isLibrarian", true);
        users.save(null, profile);
        return token(username);
    }

    long book(String code, String title) {
        return db.insert("INSERT INTO books(code,title,purchase_year) VALUES(?,?,?)",
                code, title, Integer.parseInt(code.substring(0, 4)));
    }

    long borrow(long userId, long bookId, LocalDate dueDate, LocalDate returnedDate) {
        return db.insert("INSERT INTO borrows(user_id,book_id,borrowed_at,due_at,returned_at) VALUES(?,?,?,?,?)",
                userId, bookId, BorrowService.stamp(dueDate.minusDays(30).atStartOfDay()), BorrowService.stamp(dueDate.atStartOfDay()),
                returnedDate == null ? null : BorrowService.stamp(returnedDate.atStartOfDay()));
    }

    MockHttpServletRequestBuilder with(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }

    JsonNode response(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void validationRuleRejectsInvalidProfileAndValidateEndpointReturnsFields() throws Exception {
        var bad = new HashMap<String, Object>(Map.of("username", "bad-teacher", "password", "Test-Admin-2026",
                "identityType", "teacher", "name", "测试教师", "teacherType", "full", "employeeNo", "7520200001"));
        assertThatThrownBy(() -> users.save(null, bad)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code).isEqualTo(422));
        mvc.perform(with(post("/api/v1/validate"), admin).contentType("application/json")
                        .content("{\"type\":\"student\",\"fields\":{\"studentNo\":\"3420260001\",\"name\":\"测试学生\",\"status\":\"在读\"}}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.data.fields.studentNo").exists());
        mvc.perform(with(post("/api/v1/validate"), admin).contentType("application/json")
                        .content("{\"type\":\"student\",\"fields\":{\"studentNo\":\"3120260001\",\"name\":\"测试学生\",\"status\":\"在读\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(true));
        mvc.perform(post("/api/v1/validate").contentType("application/json")
                        .content("{\"type\":\"student\",\"fields\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void photoCheckReportsSizeAndRatioWithoutBlocking() throws Exception {
        byte[] valid;
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(330, 480, BufferedImage.TYPE_INT_RGB), "png", out);
            valid = out.toByteArray();
        }
        var ok = new MockMultipartFile("file", "photo.png", "image/png", valid);
        mvc.perform(with(multipart("/api/v1/photos/check").file(ok), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sizeOk").value(true))
                .andExpect(jsonPath("$.data.ratioOk").value(true));

        byte[] square;
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", out);
            square = out.toByteArray();
        }
        mvc.perform(with(multipart("/api/v1/photos/check").file(new MockMultipartFile("file", "square.png", "image/png", square)), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ratioOk").value(false))
                .andExpect(jsonPath("$.data.suggestions").isNotEmpty());

        byte[] huge = new byte[3 * 1024 * 1024];
        mvc.perform(with(multipart("/api/v1/photos/check").file(new MockMultipartFile("file", "huge.png", "image/png", huge)), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sizeOk").value(false));
    }

    @Test
    void borrowFlowCoversApprovalDueDateReturnAndCooldown() throws Exception {
        long userId = student("student", "3120260001");
        String studentToken = token("student");
        String librarianToken = librarian("librarian", "7220100001");
        long bookId = book("2026001", "数据库测试");

        var created = response(mvc.perform(with(post("/api/v1/books/" + bookId + "/borrow-requests"), studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("pending")));
        long requestId = created.at("/data/id").asLong();
        mvc.perform(with(post("/api/v1/books/" + bookId + "/borrow-requests"), studentToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.rule").value("DUPLICATE_REQUEST"));

        var approved = response(mvc.perform(with(post("/api/v1/admin/borrow-requests/" + requestId + "/approve"), librarianToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("在借")));
        long borrowId = approved.at("/data/id").asLong();
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM borrows WHERE request_id=?", Long.class, requestId)).isEqualTo(1);
        assertThat(db.one("SELECT due_at FROM borrows WHERE id=?", borrowId).get("due_at")).isNotNull();

        mvc.perform(with(post("/api/v1/admin/borrow-requests/" + requestId + "/approve"), librarianToken))
                .andExpect(status().isConflict());

        mvc.perform(with(post("/api/v1/admin/borrows/" + borrowId + "/return"), librarianToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("已归还"));
        mvc.perform(with(post("/api/v1/admin/borrows/" + borrowId + "/return"), librarianToken))
                .andExpect(status().isConflict());

        mvc.perform(with(post("/api/v1/books/" + bookId + "/borrow-requests"), studentToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.rule").value("COOLDOWN"));

        db.jdbc.update("UPDATE books SET last_returned_at=? WHERE id=?", BorrowService.stamp(BorrowService.now().minusDays(6)), bookId);
        mvc.perform(with(post("/api/v1/books/" + bookId + "/borrow-requests"), studentToken)).andExpect(status().isOk());
        assertThat(db.jdbc.queryForObject("SELECT returned_by FROM borrows WHERE id=?", Long.class, borrowId)).isNotNull();
    }

    @Test
    void maxBorrowLimitFollowsConfigurableValue() throws Exception {
        student("student", "3120260001");
        String studentToken = token("student");
        String librarianToken = librarian("librarian", "7220100001");
        long first = book("2026001", "图书一");
        long second = book("2026002", "图书二");
        long third = book("2026003", "图书三");
        approve(request(studentToken, first), librarianToken);
        approve(request(studentToken, second), librarianToken);
        mvc.perform(with(post("/api/v1/books/" + third + "/borrow-requests"), studentToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.rule").value("MAX_BORROW"))
                .andExpect(jsonPath("$.data.limit").value(2));
        mvc.perform(with(put("/api/v1/admin/library/config"), librarianToken).contentType("application/json")
                        .content("{\"maxBorrow\":3,\"loanDays\":30,\"remindBeforeWorkdays\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.maxBorrow").value(3));
        mvc.perform(with(post("/api/v1/books/" + third + "/borrow-requests"), studentToken)).andExpect(status().isOk());
    }

    @Test
    void stopBorrowingStartsAtFourthConfirmedViolation() throws Exception {
        long userId = student("student", "3120260001");
        String studentToken = token("student");
        long target = book("2026009", "目标图书");
        for (int i = 0; i < 3; i++) {
            long pastBook = book("20260" + (i + 1) + "0", "历史图书" + i);
            long borrowId = borrow(userId, pastBook, LocalDate.now().minusDays(60), LocalDate.now().minusDays(40));
            db.jdbc.update("INSERT INTO violations(user_id,borrow_id,status,reason) VALUES(?,?,'confirmed','测试')", userId, borrowId);
        }
        mvc.perform(with(post("/api/v1/books/" + target + "/borrow-requests"), studentToken)).andExpect(status().isOk());
        long fourthBook = book("2026019", "第四册");
        long borrowId = borrow(userId, fourthBook, LocalDate.now().minusDays(60), LocalDate.now().minusDays(40));
        db.jdbc.update("INSERT INTO violations(user_id,borrow_id,status,reason) VALUES(?,?,'confirmed','测试')", userId, borrowId);
        long another = book("2026029", "第五册");
        mvc.perform(with(post("/api/v1/books/" + another + "/borrow-requests"), studentToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.rule").value("SUSPENDED"));
        mvc.perform(with(get("/api/v1/me/violations"), studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmedCount").value(4))
                .andExpect(jsonPath("$.data.suspended").value(true));
    }

    @Test
    void schedulerRemindsOncePerDayAndCreatesViolationCandidate() throws Exception {
        long userId = student("student", "3120260001");
        librarian("librarian", "7220100001");
        long soonBook = book("2026001", "即将到期");
        long overdueBook = book("2026002", "已逾期");
        long soon = borrow(userId, soonBook, LocalDate.now().plusDays(1), null);
        long overdue = borrow(userId, overdueBook, LocalDate.now().minusDays(30), null);

        var first = scheduler.scan(LocalDate.now());
        assertThat(first.get("dueReminders")).isNotNull();
        long dueCount = db.jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE dedup_key LIKE ?", Long.class, "due:" + soon + ":%");
        assertThat(dueCount).isEqualTo(1);
        long candidate = db.jdbc.queryForObject("SELECT COUNT(*) FROM violations WHERE borrow_id=? AND status='pending'", Long.class, overdue);
        assertThat(candidate).isEqualTo(1);
        long totalNotifications = db.jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class);

        scheduler.scan(LocalDate.now());
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isEqualTo(totalNotifications);
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM violations WHERE borrow_id=?", Long.class, overdue)).isEqualTo(1);
    }

    @Test
    void violationConfirmationRequiresLibrarianAndCannotRepeat() throws Exception {
        long userId = student("student", "3120260001");
        String librarianToken = librarian("librarian", "7220100001");
        long pastBook = book("2026001", "逾期图书");
        long borrowId = borrow(userId, pastBook, LocalDate.now().minusDays(40), null);
        db.jdbc.update("INSERT INTO violations(user_id,borrow_id,status,reason) VALUES(?,?,'pending','逾期测试')", userId, borrowId);
        long violationId = Db.id(db.one("SELECT id FROM violations WHERE borrow_id=?", borrowId).get("id"));

        mvc.perform(with(get("/api/v1/admin/violations").param("status", "pending"), admin)).andExpect(status().isForbidden());
        mvc.perform(with(post("/api/v1/admin/violations"), admin).contentType("application/json")
                .content("{\"id\":" + violationId + "}")).andExpect(status().isForbidden());
        mvc.perform(with(post("/api/v1/admin/violations"), librarianToken).contentType("application/json")
                        .content("{\"id\":" + violationId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("confirmed"));
        mvc.perform(with(post("/api/v1/admin/violations"), librarianToken).contentType("application/json")
                .content("{\"id\":" + violationId + "}")).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/admin/violations/" + violationId)).andExpect(status().isNotFound());
    }

    @Test
    void downloadsRequireLoginAndSupportAdminMaintenance() throws Exception {
        byte[] bytes = "开题报告模板".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "template.txt", "text/plain", bytes);
        mvc.perform(get("/api/v1/downloads")).andExpect(status().isUnauthorized());
        var saved = response(mvc.perform(with(multipart("/api/v1/admin/downloads").file(file)
                .param("category", "开题").param("title", "开题报告模板"), admin)).andExpect(status().isOk()));
        long id = saved.at("/data/id").asLong();
        mvc.perform(with(get("/api/v1/downloads").param("category", "开题"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(with(get("/api/v1/downloads/" + id + "/file"), admin))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));
        mvc.perform(get("/api/v1/downloads/" + id + "/file")).andExpect(status().isUnauthorized());
        mvc.perform(with(multipart("/api/v1/admin/downloads").file(file)
                .param("category", "未知分类").param("title", "错误"), admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(with(delete("/api/v1/admin/downloads/" + id), admin)).andExpect(status().isOk());
        mvc.perform(with(get("/api/v1/downloads"), admin)).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void helpTreeIsReturnedAndKeywordFiltersMatches() throws Exception {
        mvc.perform(with(get("/api/v1/help"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tree").isNotEmpty());
        mvc.perform(with(get("/api/v1/help").param("keyword", "冷却"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matches").isNotEmpty());
        mvc.perform(get("/api/v1/help")).andExpect(status().isUnauthorized());
    }

    @Test
    void bookAdminCrudAndImportReportRowLevelResults() throws Exception {
        String librarianToken = librarian("librarian", "7220100001");
        mvc.perform(with(post("/api/v1/admin/books"), librarianToken).contentType("application/json")
                        .content("{\"code\":\"2026001\",\"title\":\"图书一\",\"author\":\"作者\",\"purchaseYear\":2026}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("2026001"));
        mvc.perform(with(post("/api/v1/admin/books"), librarianToken).contentType("application/json")
                        .content("{\"code\":\"2027001\",\"title\":\"错误年份\",\"purchaseYear\":2026}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(with(post("/api/v1/admin/books"), admin).contentType("application/json")
                .content("{\"code\":\"2026002\",\"title\":\"越权\",\"purchaseYear\":2026}")).andExpect(status().isForbidden());

        String csv = "图书编号,图书名称,作者\n2026003,图书三,作者甲\n2026,图书错,作者乙\n";
        var file = new MockMultipartFile("file", "books.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        mvc.perform(with(multipart("/api/v1/admin/books/import").file(file), librarianToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(1))
                .andExpect(jsonPath("$.data.failed").value(1));
    }

    @Test
    void notificationsAreScopedToOwner() throws Exception {
        long first = student("first", "3120260001");
        student("second", "3120260002");
        String firstToken = token("first");
        String secondToken = token("second");
        db.jdbc.update("INSERT INTO notifications(user_id,title,body,dedup_key) VALUES(?,?,?,'manual:1')", first, "提醒", "内容");
        long id = Db.id(db.one("SELECT id FROM notifications WHERE dedup_key='manual:1'").get("id"));
        mvc.perform(with(put("/api/v1/notifications/" + id + "/read"), secondToken)).andExpect(status().isNotFound());
        mvc.perform(with(put("/api/v1/notifications/" + id + "/read"), firstToken)).andExpect(status().isOk());
        mvc.perform(with(get("/api/v1/notifications"), firstToken)).andExpect(jsonPath("$.data.items[0].isRead").value(true));
    }

    @Test
    void myBorrowsExposesDueReminderFlag() throws Exception {
        long userId = student("student", "3120260001");
        String studentToken = token("student");
        long soon = book("2026001", "即将到期");
        long later = book("2026002", "远期到期");
        borrow(userId, soon, LocalDate.now().plusDays(1), null);
        borrow(userId, later, LocalDate.now().plusDays(40), null);
        var body = response(mvc.perform(with(get("/api/v1/me/borrows"), studentToken)).andExpect(status().isOk()));
        var flags = new LinkedHashMap<String, Boolean>();
        body.at("/data/items").forEach(item -> flags.put(item.at("/bookCode").asText(), item.at("/dueReminder").asBoolean()));
        assertThat(flags).containsEntry("2026001", true).containsEntry("2026002", false);
    }

    private long request(String studentToken, long bookId) throws Exception {
        var result = response(mvc.perform(with(post("/api/v1/books/" + bookId + "/borrow-requests"), studentToken))
                .andExpect(status().isOk()));
        return result.at("/data/id").asLong();
    }

    private void approve(long requestId, String librarianToken) throws Exception {
        mvc.perform(with(post("/api/v1/admin/borrow-requests/" + requestId + "/approve"), librarianToken))
                .andExpect(status().isOk());
    }
}