package cn.sqa2.nacams.library;

import cn.sqa2.nacams.common.*;
import cn.sqa2.nacams.portal.MediaService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class DownloadService {
    public static final List<String> CATEGORIES = List.of("开题", "中期", "答辩", "学位论文", "专利", "会议", "上级文件", "其他");
    private final Db db;
    private final MediaService media;
    public DownloadService(Db db, MediaService media) { this.db = db; this.media = media; }

    public Map<String, Object> list(String category, String keyword, int page, int size) {
        var args = new ArrayList<Object>();
        String where = " FROM downloads d LEFT JOIN media_files m ON m.id=d.media_id WHERE 1=1";
        if (!category.isBlank()) {
            requireCategory(category);
            where += " AND d.category=?"; args.add(category);
        }
        if (!keyword.isBlank()) {
            where += " AND d.title LIKE ?"; args.add("%" + keyword + "%");
        }
        long total = db.jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        int offset = Db.page(page, size);
        var values = new ArrayList<>(args); values.add(size); values.add(offset);
        var rows = db.jdbc.queryForList("SELECT d.id,d.category,d.title,d.sort_order,m.original_name,m.size_bytes,m.content_type"
                + where + " ORDER BY d.sort_order,d.id LIMIT ? OFFSET ?", values.toArray());
        var items = rows.stream().map(row -> {
            var item = new LinkedHashMap<String, Object>();
            item.put("id", Db.id(row.get("id")));
            item.put("category", Db.str(row, "category"));
            item.put("title", Db.str(row, "title"));
            item.put("fileName", row.get("original_name"));
            item.put("size", row.get("size_bytes"));
            item.put("contentType", row.get("content_type"));
            item.put("sortOrder", row.get("sort_order"));
            return item;
        }).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("items", items); result.put("total", total); result.put("page", page); result.put("page_size", size);
        result.put("categories", CATEGORIES);
        return result;
    }

    public Map<String, Object> get(long id) {
        var row = db.one("SELECT d.id,d.category,d.title,d.sort_order,m.original_name,m.size_bytes,m.content_type"
                + " FROM downloads d LEFT JOIN media_files m ON m.id=d.media_id WHERE d.id=?", id);
        var item = new LinkedHashMap<String, Object>();
        item.put("id", Db.id(row.get("id")));
        item.put("category", Db.str(row, "category"));
        item.put("title", Db.str(row, "title"));
        item.put("fileName", row.get("original_name"));
        item.put("size", row.get("size_bytes"));
        item.put("contentType", row.get("content_type"));
        item.put("sortOrder", row.get("sort_order"));
        return item;
    }

    public ResponseEntity<?> file(long id) {
        var row = db.one("SELECT d.title AS download_title,m.storage_name,m.original_name,m.content_type"
                + " FROM downloads d JOIN media_files m ON m.id=d.media_id WHERE d.id=?", id);
        String mime = Db.str(row, "content_type");
        if (mime.isBlank()) mime = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        var disposition = ContentDisposition.attachment().filename(Db.str(row, "original_name"), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(mime))
                .body(new FileSystemResource(media.path(Db.str(row, "storage_name"))));
    }

    public Map<String, Object> create(String category, String title, int sortOrder, MultipartFile file) {
        requireCategory(category);
        String cleanTitle = requireTitle(title);
        if (file == null || file.isEmpty()) throw new ApiException(422, "缺少上传文件file");
        var saved = media.savePrivate(file);
        long id = db.insert("INSERT INTO downloads(category,title,media_id,sort_order) VALUES(?,?,?,?)",
                category, cleanTitle, saved.get("id"), sortOrder);
        return get(id);
    }

    public Map<String, Object> update(long id, String category, String title, Integer sortOrder, MultipartFile file) {
        var current = db.one("SELECT * FROM downloads WHERE id=?", id);
        String nextCategory = category == null || category.isBlank() ? Db.str(current, "category") : category;
        requireCategory(nextCategory);
        String nextTitle = title == null || title.isBlank() ? Db.str(current, "title") : requireTitle(title);
        int nextOrder = sortOrder == null ? ((Number) current.get("sort_order")).intValue() : sortOrder;
        Object mediaId = current.get("media_id");
        if (file != null && !file.isEmpty()) mediaId = media.savePrivate(file).get("id");
        db.jdbc.update("UPDATE downloads SET category=?,title=?,media_id=?,sort_order=? WHERE id=?",
                nextCategory, nextTitle, mediaId, nextOrder, id);
        return get(id);
    }

    public void delete(long id) {
        if (db.jdbc.update("DELETE FROM downloads WHERE id=?", id) == 0) throw new ApiException(404, "下载条目不存在");
    }

    private static void requireCategory(String category) {
        if (!CATEGORIES.contains(category)) throw new ApiException(422, "分类须为：" + String.join("/", CATEGORIES));
    }

    private static String requireTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty() || value.length() > 500) throw new ApiException(422, "标题必填且不超过500字符");
        return value;
    }
}