package cn.sqa2.nacams.library;

import cn.sqa2.nacams.common.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class BorrowService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final String BORROWING = "在借";
    public static final String OVERDUE = "已超期";
    public static final String RETURNED = "已归还";
    private static final int COOLDOWN_DAYS = 5;
    private static final int STOP_THRESHOLD = 3;
    private final Db db;
    private final LibraryConfigService config;
    private final NotificationService notifications;

    public BorrowService(Db db, LibraryConfigService config, NotificationService notifications) {
        this.db = db; this.config = config; this.notifications = notifications;
    }

    public static LocalDateTime now() { return LocalDateTime.now(ZONE); }
    public static Timestamp stamp(LocalDateTime value) { return Timestamp.valueOf(value); }

    public Map<String, Object> books(String keyword, int page, int size) {
        var args = new ArrayList<Object>();
        String where = "FROM books WHERE 1=1";
        if (!keyword.isBlank()) {
            where += " AND (code LIKE ? OR legacy_code LIKE ? OR title LIKE ? OR author LIKE ?)";
            String like = "%" + keyword + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        long total = db.jdbc.queryForObject("SELECT COUNT(*) " + where, Long.class, args.toArray());
        int offset = Db.page(page, size);
        var values = new ArrayList<>(args); values.add(size); values.add(offset);
        var rows = db.jdbc.queryForList("SELECT * " + where + " ORDER BY code LIMIT ? OFFSET ?", values.toArray());
        var items = rows.stream().map(this::bookView).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("items", items); result.put("total", total); result.put("page", page); result.put("page_size", size);
        return result;
    }

    public Map<String, Object> bookView(Map<String, Object> row) {
        long id = Db.id(row.get("id"));
        var item = new LinkedHashMap<String, Object>();
        item.put("id", id);
        item.put("code", Db.str(row, "code"));
        item.put("legacyCode", nullable(row.get("legacy_code")));
        item.put("title", Db.str(row, "title"));
        item.put("author", nullable(row.get("author")));
        item.put("purchaseYear", row.get("purchase_year"));
        item.put("publicationYear", row.get("publication_year"));
        item.put("location", nullable(row.get("location")));
        item.put("lastReturnedAt", row.get("last_returned_at"));
        var state = availability(id, row.get("last_returned_at"));
        item.put("status", state.get("status"));
        item.put("availableAt", state.get("availableAt"));
        return item;
    }

    private Map<String, Object> availability(long bookId, Object lastReturnedAt) {
        long active = db.jdbc.queryForObject("SELECT COUNT(*) FROM borrows WHERE book_id=? AND returned_at IS NULL", Long.class, bookId);
        if (active > 0) return Map.of("status", "已借出");
        LocalDateTime available = cooldownEnd(lastReturnedAt);
        if (available != null && available.isAfter(now())) {
            return Map.of("status", "冷却中", "availableAt", available.toLocalDate().toString());
        }
        return Map.of("status", "可借");
    }

    private static LocalDateTime cooldownEnd(Object lastReturnedAt) {
        LocalDateTime returned = time(lastReturnedAt);
        return returned == null ? null : returned.plusDays(COOLDOWN_DAYS);
    }

    public static LocalDateTime time(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof LocalDateTime dt) return dt;
        if (value instanceof java.time.OffsetDateTime odt) return odt.toLocalDateTime();
        if (value instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
        if (value instanceof Date d) return LocalDateTime.ofInstant(d.toInstant(), ZONE);
        String text = value.toString().trim().replace('T', ' ');
        if (text.length() > 19) text = text.substring(0, 19);
        if (text.length() == 10) text += " 00:00:00";
        return LocalDateTime.parse(text.replace(' ', 'T'));
    }

    public static LocalDate date(Object value) {
        LocalDateTime value1 = time(value);
        return value1 == null ? null : value1.toLocalDate();
    }

    @Transactional
    public Map<String, Object> request(long userId, long bookId) {
        checkEligible(userId, bookId, null);
        long id = db.insert("INSERT INTO borrow_requests(user_id,book_id,status) VALUES(?,?,'pending')", userId, bookId);
        return requestView(id);
    }

    public void checkEligible(long userId, long bookId, Long ignoreRequestId) {
        var book = db.one("SELECT * FROM books WHERE id=?", bookId);
        var config1 = config.get();
        long activeCopy = db.jdbc.queryForObject("SELECT COUNT(*) FROM borrows WHERE book_id=? AND returned_at IS NULL", Long.class, bookId);
        if (activeCopy > 0) {
            throw new ApiException(409, "该图书已借出，归还后方可申请", Map.of("rule", "BOOK_ON_LOAN",
                    "code", Db.str(book, "code"), "title", Db.str(book, "title")));
        }
        long current = db.jdbc.queryForObject("SELECT COUNT(*) FROM borrows WHERE user_id=? AND returned_at IS NULL", Long.class, userId);
        if (current >= config1.maxBorrow()) {
            throw new ApiException(409, "在借图书已达上限" + config1.maxBorrow() + "册，请先归还",
                    Map.of("rule", "MAX_BORROW", "current", current, "limit", config1.maxBorrow()));
        }
        LocalDateTime cooldown = cooldownEnd(book.get("last_returned_at"));
        if (cooldown != null && cooldown.isAfter(now())) {
            throw new ApiException(409, "该图书归还后5个自然日内不可再借",
                    Map.of("rule", "COOLDOWN", "availableAt", cooldown.toLocalDate().toString()));
        }
        long confirmed = db.jdbc.queryForObject("SELECT COUNT(*) FROM violations WHERE user_id=? AND status='confirmed'", Long.class, userId);
        if (confirmed > STOP_THRESHOLD) {
            throw new ApiException(409, "已确认违规超过3次，借阅资格已停止",
                    Map.of("rule", "SUSPENDED", "confirmed", confirmed, "threshold", STOP_THRESHOLD));
        }
        long pending = ignoreRequestId == null
                ? db.jdbc.queryForObject("SELECT COUNT(*) FROM borrow_requests WHERE user_id=? AND book_id=? AND status='pending'", Long.class, userId, bookId)
                : db.jdbc.queryForObject("SELECT COUNT(*) FROM borrow_requests WHERE user_id=? AND book_id=? AND status='pending' AND id<>?", Long.class, userId, bookId, ignoreRequestId);
        if (pending > 0) throw new ApiException(409, "该图书已有待审核申请，请勿重复提交",
                Map.of("rule", "DUPLICATE_REQUEST"));
    }

    public Map<String, Object> requestView(long id) {
        var row = db.one("SELECT r.*,b.code,b.title,u.name AS user_name FROM borrow_requests r JOIN books b ON b.id=r.book_id JOIN users u ON u.id=r.user_id WHERE r.id=?", id);
        var item = new LinkedHashMap<String, Object>();
        item.put("id", Db.id(row.get("id")));
        item.put("bookId", Db.id(row.get("book_id")));
        item.put("userId", Db.id(row.get("user_id")));
        item.put("status", Db.str(row, "status"));
        item.put("bookCode", Db.str(row, "code"));
        item.put("bookTitle", Db.str(row, "title"));
        item.put("userName", Db.str(row, "user_name"));
        item.put("requestedAt", row.get("requested_at"));
        item.put("reviewedBy", row.get("reviewed_by"));
        item.put("reviewedAt", row.get("reviewed_at"));
        item.put("reason", row.get("review_note"));
        return item;
    }

    public Map<String, Object> requests(String status, int page, int size) {
        var args = new ArrayList<Object>();
        String from = " FROM borrow_requests r JOIN books b ON b.id=r.book_id JOIN users u ON u.id=r.user_id";
        if (!status.isBlank()) { from += " WHERE r.status=?"; args.add(status); }
        long total = db.jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        int offset = Db.page(page, size);
        var values = new ArrayList<>(args); values.add(size); values.add(offset);
        var rows = db.jdbc.queryForList("SELECT r.id" + from + " ORDER BY r.requested_at DESC,r.id DESC LIMIT ? OFFSET ?", values.toArray());
        var result = new LinkedHashMap<String, Object>();
        result.put("items", rows.stream().map(row -> requestView(Db.id(row.get("id")))).toList());
        result.put("total", total); result.put("page", page); result.put("page_size", size);
        return result;
    }

    @Transactional
    public Map<String, Object> approve(long requestId, long librarianId) {
        var request = db.one("SELECT * FROM borrow_requests WHERE id=? FOR UPDATE", requestId);
        if (!"pending".equals(Db.str(request, "status"))) throw new ApiException(409, "该申请已处理，不能重复审核");
        long userId = Db.id(request.get("user_id"));
        long bookId = Db.id(request.get("book_id"));
        checkEligible(userId, bookId, requestId);
        var config1 = config.get();
        LocalDateTime borrowedAt = now();
        long borrowId = db.insert("INSERT INTO borrows(user_id,book_id,request_id,borrowed_at,due_at) VALUES(?,?,?,?,?)",
                userId, bookId, requestId, stamp(borrowedAt), stamp(borrowedAt.plusDays(config1.loanDays())));
        db.jdbc.update("UPDATE borrow_requests SET status='approved',reviewed_by=?,reviewed_at=? WHERE id=?",
                librarianId, stamp(now()), requestId);
        return borrowView(borrowId);
    }

    @Transactional
    public Map<String, Object> reject(long requestId, String reason, long librarianId) {
        var request = db.one("SELECT * FROM borrow_requests WHERE id=? FOR UPDATE", requestId);
        if (!"pending".equals(Db.str(request, "status"))) throw new ApiException(409, "该申请已处理，不能重复审核");
        String note = reason == null ? "" : reason.trim();
        if (note.length() > 500) throw new ApiException(422, "驳回原因不超过500字符");
        if (note.isEmpty()) note = "图书管理教师驳回";
        db.jdbc.update("UPDATE borrow_requests SET status='rejected',review_note=?,reviewed_by=?,reviewed_at=? WHERE id=?",
                note, librarianId, stamp(now()), requestId);
        return requestView(requestId);
    }

    public Map<String, Object> borrowView(long id) {
        var row = db.one("SELECT b.*,books.code AS book_code,books.title AS book_title,u.name AS user_name FROM borrows b JOIN books ON books.id=b.book_id JOIN users u ON u.id=b.user_id WHERE b.id=?", id);
        return borrowView(row);
    }

    public Map<String, Object> borrowView(Map<String, Object> row) {
        LocalDateTime borrowedAt = time(row.get("borrowed_at"));
        LocalDateTime dueAt = time(row.get("due_at"));
        LocalDateTime returnedAt = time(row.get("returned_at"));
        LocalDateTime current = now();
        var item = new LinkedHashMap<String, Object>();
        item.put("id", Db.id(row.get("id")));
        item.put("userId", Db.id(row.get("user_id")));
        item.put("bookId", Db.id(row.get("book_id")));
        item.put("requestId", row.get("request_id"));
        item.put("bookCode", Db.str(row, "book_code"));
        item.put("bookTitle", Db.str(row, "book_title"));
        item.put("userName", Db.str(row, "user_name"));
        item.put("borrowedAt", borrowedAt);
        item.put("dueAt", dueAt);
        item.put("returnedAt", returnedAt);
        item.put("returnedBy", row.get("returned_by"));
        String status = returnedAt != null ? RETURNED : (dueAt != null && current.isAfter(dueAt) ? OVERDUE : BORROWING);
        item.put("status", status);
        item.put("remainingDays", returnedAt != null || dueAt == null ? null : Math.max(0, ChronoUnit.DAYS.between(current.toLocalDate(), dueAt.toLocalDate())));
        item.put("overdueDays", returnedAt == null && dueAt != null && current.isAfter(dueAt) ? ChronoUnit.DAYS.between(dueAt.toLocalDate(), current.toLocalDate()) : 0);
        item.put("dueReminder", dueReminder(returnedAt, dueAt, current.toLocalDate()));
        return item;
    }

    /** 到期提醒标记：未归还且已进入"应还日期前 N 个工作日"窗口（与 LibraryScheduler 同一工作日口径）。 */
    private boolean dueReminder(LocalDateTime returnedAt, LocalDateTime dueAt, LocalDate today) {
        if (returnedAt != null || dueAt == null) return false;
        LocalDate dueDate = dueAt.toLocalDate();
        if (today.isAfter(dueDate)) return false;
        return !today.isBefore(config.minusWorkdays(dueDate, config.get().remindBeforeWorkdays()));
    }

    public Map<String, Object> myBorrows(long userId, int page, int size) {
        var args = new ArrayList<Object>();
        args.add(userId);
        return pagedBorrows(" WHERE b.user_id=?", args, page, size);
    }

    public Map<String, Object> borrows(String status, int page, int size) {
        String where;
        var args = new ArrayList<Object>();
        Timestamp current = stamp(now());
        switch (status) {
            case "" -> where = "";
            case BORROWING -> { where = " WHERE b.returned_at IS NULL AND b.due_at>=?"; args.add(current); }
            case OVERDUE -> { where = " WHERE b.returned_at IS NULL AND b.due_at<?"; args.add(current); }
            case RETURNED -> where = " WHERE b.returned_at IS NOT NULL";
            default -> throw new ApiException(400, "status仅支持在借/已超期/已归还");
        }
        return pagedBorrows(where, args, page, size);
    }

    private Map<String, Object> pagedBorrows(String where, List<Object> args, int page, int size) {
        String from = " FROM borrows b JOIN books ON books.id=b.book_id JOIN users u ON u.id=b.user_id" + where;
        long total = db.jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        int offset = Db.page(page, size);
        var values = new ArrayList<>(args); values.add(size); values.add(offset);
        var rows = db.jdbc.queryForList("SELECT b.id,b.user_id,b.book_id,b.request_id,b.borrowed_at,b.due_at,b.returned_at,b.returned_by,books.code AS book_code,books.title AS book_title,u.name AS user_name"
                + from + " ORDER BY b.borrowed_at DESC,b.id DESC LIMIT ? OFFSET ?", values.toArray());
        var result = new LinkedHashMap<String, Object>();
        result.put("items", rows.stream().map(this::borrowView).toList());
        result.put("total", total); result.put("page", page); result.put("page_size", size);
        return result;
    }

    @Transactional
    public Map<String, Object> returnBook(long borrowId, long librarianId) {
        var row = db.one("SELECT * FROM borrows WHERE id=? FOR UPDATE", borrowId);
        if (row.get("returned_at") != null) throw new ApiException(409, "该借阅记录已归还");
        LocalDateTime returnedAt = now();
        db.jdbc.update("UPDATE borrows SET returned_at=?,returned_by=? WHERE id=?", stamp(returnedAt), librarianId, borrowId);
        db.jdbc.update("UPDATE books SET last_returned_at=? WHERE id=?", stamp(returnedAt), Db.id(row.get("book_id")));
        var item = new LinkedHashMap<String, Object>();
        item.put("id", borrowId);
        item.put("returnedAt", returnedAt);
        item.put("returnedBy", librarianId);
        item.put("status", RETURNED);
        item.put("cooldownUntil", returnedAt.plusDays(COOLDOWN_DAYS).toLocalDate().toString());
        return item;
    }

    public Map<String, Object> violations(Long userId, String status, int page, int size) {
        var args = new ArrayList<Object>();
        String where = " WHERE 1=1";
        if (userId != null) { where += " AND v.user_id=?"; args.add(userId); }
        if (!status.isBlank()) {
            if (!Set.of("pending", "confirmed").contains(status)) throw new ApiException(400, "status仅支持pending/confirmed");
            where += " AND v.status=?"; args.add(status);
        }
        String from = " FROM violations v JOIN users u ON u.id=v.user_id JOIN borrows b ON b.id=v.borrow_id JOIN books ON books.id=b.book_id" + where;
        long total = db.jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        int offset = Db.page(page, size);
        var values = new ArrayList<>(args); values.add(size); values.add(offset);
        var rows = db.jdbc.queryForList("SELECT v.*,u.name AS user_name,b.due_at,b.returned_at,books.code AS book_code,books.title AS book_title,"
                + "(SELECT x.name FROM users x WHERE x.id=v.confirmed_by) AS confirmer_name" + from
                + " ORDER BY v.id DESC LIMIT ? OFFSET ?", values.toArray());
        var result = new LinkedHashMap<String, Object>();
        result.put("items", rows.stream().map(this::violationView).toList());
        result.put("total", total); result.put("page", page); result.put("page_size", size);
        return result;
    }

    public Map<String, Object> violationById(long id) {
        var row = db.one("SELECT v.*,u.name AS user_name,b.due_at,b.returned_at,books.code AS book_code,books.title AS book_title,"
                + "(SELECT x.name FROM users x WHERE x.id=v.confirmed_by) AS confirmer_name"
                + " FROM violations v JOIN users u ON u.id=v.user_id JOIN borrows b ON b.id=v.borrow_id JOIN books ON books.id=b.book_id WHERE v.id=?", id);
        return violationView(row);
    }

    private Map<String, Object> violationView(Map<String, Object> row) {
        var item = new LinkedHashMap<String, Object>();
        item.put("id", Db.id(row.get("id")));
        item.put("userId", Db.id(row.get("user_id")));
        item.put("borrowId", Db.id(row.get("borrow_id")));
        item.put("status", Db.str(row, "status"));
        item.put("reason", Db.str(row, "reason"));
        item.put("confirmedBy", row.get("confirmed_by"));
        item.put("confirmedByName", nullable(row.get("confirmer_name")));
        item.put("confirmedAt", row.get("confirmed_at"));
        item.put("userName", nullable(row.get("user_name")));
        item.put("bookCode", Db.str(row, "book_code"));
        item.put("bookTitle", Db.str(row, "book_title"));
        item.put("dueAt", row.get("due_at"));
        item.put("returnedAt", row.get("returned_at"));
        return item;
    }

    public Map<String, Object> myViolations(long userId, int page, int size) {
        var result = violations(userId, "", page, size);
        long confirmed = db.jdbc.queryForObject("SELECT COUNT(*) FROM violations WHERE user_id=? AND status='confirmed'", Long.class, userId);
        result.put("confirmedCount", confirmed);
        result.put("threshold", STOP_THRESHOLD);
        result.put("suspended", confirmed > STOP_THRESHOLD);
        return result;
    }

    @Transactional
    public Map<String, Object> confirmViolation(long id, long librarianId) {
        var row = db.one("SELECT * FROM violations WHERE id=? FOR UPDATE", id);
        if ("confirmed".equals(Db.str(row, "status"))) throw new ApiException(409, "该违规已确认，不能重复确认");
        db.jdbc.update("UPDATE violations SET status='confirmed',confirmed_by=?,confirmed_at=? WHERE id=?",
                librarianId, stamp(now()), id);
        long userId = Db.id(row.get("user_id"));
        long confirmed = confirmedCount(userId);
        notifications.notify(userId, "诚信记录已确认",
                "因图书逾期未还，图书馆已确认1条诚信违规记录。当前累计已确认 " + confirmed + " 次，超过3次将停止借阅资格。",
                "violation-confirmed:" + id);
        return violationById(id);
    }

    public long confirmedCount(long userId) {
        return db.jdbc.queryForObject("SELECT COUNT(*) FROM violations WHERE user_id=? AND status='confirmed'", Long.class, userId);
    }

    static Object nullable(Object value) { return value == null ? null : value; }
}