package cn.sqa2.nacams.library;

import cn.sqa2.nacams.common.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class NotificationService {
    private final Db db;
    public NotificationService(Db db) { this.db = db; }

    public boolean notify(long userId, String title, String body, String dedupKey) {
        int inserted = db.jdbc.update("INSERT INTO notifications(user_id,title,body,dedup_key) SELECT ?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM notifications WHERE dedup_key=?)",
                userId, title, body, dedupKey, dedupKey);
        return inserted > 0;
    }

    public List<Long> librarians() {
        return db.jdbc.queryForList("SELECT id FROM users WHERE is_librarian=TRUE AND active=TRUE ORDER BY id", Long.class);
    }

    public long notifyLibrarians(String title, String body, String dedupKey) {
        long created = 0;
        for (long id : librarians()) if (notify(id, title, body, dedupKey + ":" + id)) created++;
        return created;
    }

    public Map<String, Object> list(long userId, int page, int size) {
        long total = db.jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=?", Long.class, userId);
        int offset = Db.page(page, size);
        var rows = db.jdbc.queryForList("SELECT id,title,body,is_read,created_at FROM notifications WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                userId, size, offset);
        var items = rows.stream().map(row -> {
            var item = new LinkedHashMap<String, Object>();
            item.put("id", Db.id(row.get("id")));
            item.put("title", Db.str(row, "title"));
            item.put("body", row.get("body"));
            item.put("isRead", Db.bool(row.get("is_read")));
            item.put("createdAt", row.get("created_at"));
            return item;
        }).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("items", items); result.put("total", total); result.put("page", page); result.put("page_size", size);
        result.put("unread", db.jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND is_read=FALSE", Long.class, userId));
        return result;
    }

    public void markRead(long id, long userId) {
        int updated = db.jdbc.update("UPDATE notifications SET is_read=TRUE WHERE id=? AND user_id=?", id, userId);
        if (updated == 0) throw new ApiException(404, "提醒不存在");
    }
}