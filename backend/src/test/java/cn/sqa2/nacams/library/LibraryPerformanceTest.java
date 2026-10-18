package cn.sqa2.nacams.library;

import cn.sqa2.nacams.admin.UserService;
import cn.sqa2.nacams.auth.AuthService;
import cn.sqa2.nacams.backup.BackupService;
import cn.sqa2.nacams.common.Db;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:nacams;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.profiles.active=test", "app.storage=target/test-storage", "app.bootstrap.password=Test-Admin-2026"})
@AutoConfigureMockMvc
class LibraryPerformanceTest {
    @Autowired MockMvc mvc;
    @Autowired Db db;
    @Autowired UserService users;
    @Autowired AuthService auth;
    @Autowired LibraryScheduler scheduler;
    String admin;
    long studentId;
    String studentToken;
    String librarianToken;

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
        studentId = Db.id(users.save(null, new HashMap<>(Map.of("username", "student", "password", "Test-Admin-2026",
                "identityType", "student", "name", "测试学生", "studentNo", "3120260001", "status", "在读"))).get("id"));
        studentToken = token("student");
        var librarian = new HashMap<String, Object>(Map.of("username", "librarian", "password", "Test-Admin-2026",
                "identityType", "teacher", "name", "图书教师", "teacherType", "full", "employeeNo", "7220100001"));
        librarian.put("isLibrarian", true);
        users.save(null, librarian);
        librarianToken = token("librarian");
    }

    String token(String username) { return auth.login(username, "Test-Admin-2026").get("token").toString(); }

    long book(String code, String title) {
        return db.insert("INSERT INTO books(code,title,purchase_year) VALUES(?,?,?)",
                code, title, Integer.parseInt(code.substring(0, 4)));
    }

    private static long millis(ThrowingCallable call) throws Exception {
        long start = System.nanoTime();
        try {
            call.call();
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static double average(List<Long> samples) {
        return samples.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    @Test
    void validateLatencyIsWithin500Milliseconds() throws Exception {
        String body = "{\"type\":\"student\",\"fields\":{\"studentNo\":\"3120260001\",\"name\":\"测试学生\",\"status\":\"在读\"}}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(with(post("/api/v1/validate"), admin).contentType("application/json").content(body))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        }
        var samples = new ArrayList<Long>();
        for (int i = 0; i < 20; i++) {
            samples.add(millis(() -> mvc.perform(with(post("/api/v1/validate"), admin)
                    .contentType("application/json").content(body))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())));
        }
        double avg = average(samples);
        System.out.println("[perf] POST /validate avg=" + avg + "ms max=" + Collections.max(samples) + "ms n=20");
        assertThat(avg).isLessThanOrEqualTo(500.0);
    }

    @Test
    void bookCatalogLatencyIsWithin500Milliseconds() throws Exception {
        for (int i = 1; i <= 200; i++) book(String.format("2026%03d", i), "图书" + i);
        mvc.perform(with(get("/api/v1/books").param("keyword", "图书1").param("page_size", "20"), studentToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        var samples = new ArrayList<Long>();
        for (int i = 0; i < 10; i++) {
            samples.add(millis(() -> mvc.perform(with(get("/api/v1/books").param("keyword", "图书1")
                    .param("page_size", "20"), studentToken))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())));
        }
        double avg = average(samples);
        System.out.println("[perf] GET /books avg=" + avg + "ms max=" + Collections.max(samples) + "ms n=10");
        assertThat(avg).isLessThanOrEqualTo(500.0);
    }

    @Test
    void borrowRequestApproveReturnLatencyIsWithinBudget() throws Exception {
        var samples = new ArrayList<Long>();
        for (int i = 0; i < 3; i++) {
            long bookId = book(String.format("2026%03d", 300 + i), "借阅图书" + i);
            long requestId = requestAndApprove(bookId);
            long borrowId = Db.id(db.one("SELECT id FROM borrows WHERE request_id=?", requestId).get("id"));
            mvc.perform(with(post("/api/v1/admin/borrows/" + borrowId + "/return"), librarianToken))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        }
        for (int i = 0; i < 10; i++) {
            long bookId = book(String.format("2027%03d", 400 + i), "计时图书" + i);
            samples.add(millis(() -> {
                long requestId = requestAndApprove(bookId);
                long borrowId = Db.id(db.one("SELECT id FROM borrows WHERE request_id=?", requestId).get("id"));
                mvc.perform(with(post("/api/v1/admin/borrows/" + borrowId + "/return"), librarianToken))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
            }));
        }
        double avg = average(samples);
        System.out.println("[perf] borrow apply+approve+return avg=" + avg + "ms max=" + Collections.max(samples) + "ms n=10");
        assertThat(avg).isLessThanOrEqualTo(1500.0);
    }

    @Test
    void photoCheckLatencyIsWithinTwoSeconds() throws Exception {
        byte[] photo = jpeg(990, 1440);
        for (int i = 0; i < 2; i++) {
            mvc.perform(with(multipart("/api/v1/photos/check").file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", photo)), admin))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        }
        var samples = new ArrayList<Long>();
        for (int i = 0; i < 5; i++) {
            samples.add(millis(() -> mvc.perform(with(multipart("/api/v1/photos/check")
                    .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", photo)), admin))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())));
        }
        double avg = average(samples);
        System.out.println("[perf] POST /photos/check " + photo.length + "B avg=" + avg + "ms max=" + Collections.max(samples) + "ms n=5");
        assertThat(avg).isLessThanOrEqualTo(2000.0);
    }

    @Test
    void dailyScanHandles200ActiveBorrowsWithinFiveMinutes() throws Exception {
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= 200; i++) {
            long bookId = book(String.format("2026%03d", 500 + i), "扫描图书" + i);
            LocalDate due = i % 2 == 0 ? today.plusDays(2) : today.minusDays(31);
            db.insert("INSERT INTO borrows(user_id,book_id,borrowed_at,due_at) VALUES(?,?,?,?)",
                    studentId, bookId, BorrowService.stamp(due.minusDays(30).atStartOfDay()), BorrowService.stamp(due.atStartOfDay()));
        }
        long elapsed = millis(() -> scheduler.scan(today));
        var result = scheduler.scan(today);
        System.out.println("[perf] scheduler.scan(200 borrows) elapsed=" + elapsed + "ms, repeated=" + result);
        assertThat(elapsed).isLessThanOrEqualTo(300_000L);
        assertThat(result.get("skipped")).isEqualTo(0);
        assertThat(result.get("violationCandidates")).isEqualTo(0);
    }

    private long requestAndApprove(long bookId) throws Exception {
        var created = mvc.perform(with(post("/api/v1/books/" + bookId + "/borrow-requests"), studentToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        long requestId = com.jayway.jsonpath.JsonPath.parse(created).read("$.data.id", Long.class);
        mvc.perform(with(post("/api/v1/admin/borrow-requests/" + requestId + "/approve"), librarianToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        return requestId;
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y += 2) for (int x = 0; x < width; x += 2) {
            image.setRGB(x, y, ((x * 255 / Math.max(1, width)) << 16) | ((y * 255 / Math.max(1, height)) << 8) | 0x7f);
        }
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder with(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }
}