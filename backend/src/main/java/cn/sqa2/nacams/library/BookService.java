package cn.sqa2.nacams.library;

import cn.sqa2.nacams.admin.TabularReader;
import cn.sqa2.nacams.common.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.*;

@Service
public class BookService {
    private static final Set<String> FIELDS = Set.of("code", "legacyCode", "title", "author", "purchaseYear", "publicationYear", "location");
    private final Db db;
    private final TabularReader reader;
    public BookService(Db db, TabularReader reader) { this.db = db; this.reader = reader; }

    public Map<String, Object> list(String keyword, int page, int size) {
        var args = new ArrayList<Object>();
        String where = " FROM books WHERE 1=1";
        if (!keyword.isBlank()) {
            where += " AND (code LIKE ? OR legacy_code LIKE ? OR title LIKE ? OR author LIKE ?)";
            String like = "%" + keyword + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        long total = db.jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        int offset = Db.page(page, size);
        var values = new ArrayList<>(args); values.add(size); values.add(offset);
        var rows = db.jdbc.queryForList("SELECT *" + where + " ORDER BY code LIMIT ? OFFSET ?", values.toArray());
        var result = new LinkedHashMap<String, Object>();
        result.put("items", rows.stream().map(this::view).toList());
        result.put("total", total); result.put("page", page); result.put("page_size", size);
        return result;
    }

    public Map<String, Object> get(long id) { return view(db.one("SELECT * FROM books WHERE id=?", id)); }

    public Map<String, Object> view(Map<String, Object> row) {
        var item = new LinkedHashMap<String, Object>();
        item.put("id", Db.id(row.get("id")));
        item.put("code", Db.str(row, "code"));
        item.put("legacyCode", row.get("legacy_code"));
        item.put("title", Db.str(row, "title"));
        item.put("author", row.get("author"));
        item.put("purchaseYear", row.get("purchase_year"));
        item.put("publicationYear", row.get("publication_year"));
        item.put("location", row.get("location"));
        item.put("lastReturnedAt", row.get("last_returned_at"));
        return item;
    }

    public Map<String, Object> create(Map<String, Object> body) {
        var book = validate(body, null);
        long id = db.insert("INSERT INTO books(code,legacy_code,title,author,purchase_year,publication_year,location) VALUES(?,?,?,?,?,?,?)",
                book.get("code"), book.get("legacyCode"), book.get("title"), book.get("author"),
                book.get("purchaseYear"), book.get("publicationYear"), book.get("location"));
        return get(id);
    }

    public Map<String, Object> update(long id, Map<String, Object> body) {
        var current = get(id);
        for (String key : body.keySet()) if (!FIELDS.contains(key)) throw new ApiException(400, "不支持的字段：" + key);
        var merged = new LinkedHashMap<String, Object>(current);
        merged.putAll(body);
        var book = validate(merged, id);
        db.jdbc.update("UPDATE books SET code=?,legacy_code=?,title=?,author=?,purchase_year=?,publication_year=?,location=? WHERE id=?",
                book.get("code"), book.get("legacyCode"), book.get("title"), book.get("author"),
                book.get("purchaseYear"), book.get("publicationYear"), book.get("location"), id);
        return get(id);
    }

    public void delete(long id) {
        db.one("SELECT id FROM books WHERE id=?", id);
        db.jdbc.update("DELETE FROM books WHERE id=?", id);
    }

    private Map<String, Object> validate(Map<String, Object> body, Long id) {
        String code = Db.str(body, "code");
        if (!code.matches("\\d{7}")) throw new ApiException(422, "图书编号须为7位数字（前4位购买年份+3位顺序号）");
        int purchaseYear = integer(body.get("purchaseYear"), "purchaseYear");
        if (purchaseYear < 1900 || purchaseYear > 2100) throw new ApiException(422, "购买年份需在1900至2100之间");
        if (Integer.parseInt(code.substring(0, 4)) != purchaseYear)
            throw new ApiException(422, "图书编号前4位须与购买年份一致");
        String title = Db.str(body, "title");
        if (title.isEmpty() || title.length() > 500) throw new ApiException(422, "书名必填且不超过500字符");
        String author = Db.str(body, "author");
        if (author.length() > 500) throw new ApiException(422, "作者不超过500字符");
        String location = Db.str(body, "location");
        if (location.length() > 200) throw new ApiException(422, "存放位置不超过200字符");
        String legacy = Db.str(body, "legacyCode");
        if (legacy.length() > 32) throw new ApiException(422, "原编号不超过32字符");
        Long duplicates = db.jdbc.queryForObject("SELECT COUNT(*) FROM books WHERE code=? AND id<>?", Long.class, code, id == null ? -1L : id);
        if (duplicates != null && duplicates > 0) throw new ApiException(409, "图书编号已存在：" + code);
        var result = new LinkedHashMap<String, Object>();
        result.put("code", code);
        result.put("legacyCode", legacy.isEmpty() ? null : legacy);
        result.put("title", title);
        result.put("author", author.isEmpty() ? null : author);
        result.put("purchaseYear", purchaseYear);
        result.put("publicationYear", body.get("publicationYear") == null || Db.str(body, "publicationYear").isEmpty() ? null : integer(body.get("publicationYear"), "publicationYear"));
        result.put("location", location.isEmpty() ? null : location);
        return result;
    }

    public Map<String, Object> importBooks(MultipartFile file, Integer purchaseYear) {
        var rows = reader.read(file);
        int success = 0, failed = 0, skipped = 0;
        var details = new ArrayList<Map<String, Object>>();
        for (var row : rows) {
            var values = row.values();
            String rawCode = first(values, "图书编号", "编号", "code");
            var detail = new LinkedHashMap<String, Object>();
            detail.put("row", row.number());
            try {
                String code = normalizeCode(rawCode, purchaseYear);
                if (db.jdbc.queryForObject("SELECT COUNT(*) FROM books WHERE code=?", Long.class, code) > 0) {
                    skipped++;
                    detail.put("ok", true);
                    detail.put("skipped", true);
                    detail.put("reason", "编号已存在，未覆盖");
                    detail.put("code", code);
                    details.add(detail);
                    continue;
                }
                int year = Integer.parseInt(code.substring(0, 4));
                var body = new LinkedHashMap<String, Object>();
                body.put("code", code);
                body.put("title", first(values, "图书名称", "书名", "title"));
                body.put("author", first(values, "作者", "author"));
                body.put("purchaseYear", year);
                body.put("publicationYear", first(values, "出版年份", "publicationYear"));
                body.put("location", first(values, "存放位置", "位置", "location"));
                if (rawCode.matches("B\\d{3}")) body.put("legacyCode", rawCode);
                create(body);
                success++;
                detail.put("ok", true);
                detail.put("code", code);
            } catch (RuntimeException e) {
                failed++;
                detail.put("ok", false);
                detail.put("code", rawCode);
                detail.put("reason", e.getMessage() == null ? "导入失败" : e.getMessage());
            }
            details.add(detail);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("skipped", skipped);
        result.put("rows", details);
        return result;
    }

    private static String normalizeCode(String rawCode, Integer purchaseYear) {
        if (rawCode.matches("\\d{7}")) return rawCode;
        if (rawCode.matches("B\\d{3}")) {
            Integer year = purchaseYear;
            if (year == null || year < 1900 || year > 2100) year = LocalDate.now(BorrowService.ZONE).getYear();
            return String.format("%04d%03d", year, Integer.parseInt(rawCode.substring(1)));
        }
        throw new ApiException(422, "图书编号须为7位数字或B001形式");
    }

    private static String first(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            String value = Db.str(values, key);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static int integer(Object value, String field) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(Objects.toString(value, "").trim());
        } catch (NumberFormatException e) {
            throw new ApiException(422, field + "须为整数");
        }
    }
}