package cn.sqa2.nacams.library;

import cn.sqa2.nacams.common.Db;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class LibraryScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(LibraryScheduler.class);
    private final Db db;
    private final LibraryConfigService config;
    private final NotificationService notifications;

    public LibraryScheduler(Db db, LibraryConfigService config, NotificationService notifications) {
        this.db = db; this.config = config; this.notifications = notifications;
    }

    @Scheduled(cron = "${app.library.scan-cron:0 30 1 * * *}", zone = "Asia/Shanghai")
    public void scheduled() {
        try {
            scan(LocalDate.now(BorrowService.ZONE));
        } catch (Exception e) {
            LOG.error("图书到期扫描失败", e);
        }
    }

    public Map<String, Object> scan(LocalDate today) {
        var settings = config.get();
        LocalDate reminderDate = config.minusWorkdays(today, settings.remindBeforeWorkdays());
        var rows = db.jdbc.queryForList("SELECT b.id,b.user_id,b.book_id,b.due_at,books.code,books.title,u.active"
                + " FROM borrows b JOIN books ON books.id=b.book_id JOIN users u ON u.id=b.user_id WHERE b.returned_at IS NULL");
        int dueReminders = 0, overdueReminders = 0, candidates = 0, skipped = 0;
        for (var row : rows) {
            if (!Db.bool(row.get("active"))) { skipped++; continue; }
            long borrowId = Db.id(row.get("id"));
            long userId = Db.id(row.get("user_id"));
            LocalDate dueDate = BorrowService.date(row.get("due_at"));
            if (dueDate == null) { skipped++; continue; }
            String code = Db.str(row, "code");
            String title = Db.str(row, "title");
            if (!today.isAfter(dueDate) && !today.isBefore(reminderDate)) {
                long remaining = Math.max(0, config.workdaysBetween(today, dueDate));
                if (notifications.notify(userId, "图书归还提醒",
                        "《" + title + "》（" + code + "）应于 " + dueDate + " 归还，剩余 " + remaining + " 个工作日。",
                        "due:" + borrowId + ":" + today)) dueReminders++;
                notifications.notifyLibrarians("图书到期提醒",
                        "借阅人（用户 #" + userId + "）所借《" + title + "》（" + code + "）将于 " + dueDate + " 到期。",
                        "due-admin:" + borrowId + ":" + today);
            }
            if (today.isAfter(dueDate)) {
                long days = ChronoUnit.DAYS.between(dueDate, today);
                if (notifications.notify(userId, "图书逾期提醒",
                        "《" + title + "》（" + code + "）已逾期 " + days + " 天，应还日期为 " + dueDate + "，请尽快归还。",
                        "overdue:" + borrowId + ":" + today)) overdueReminders++;
                if (days >= 30) {
                    int inserted = db.jdbc.update("INSERT INTO violations(user_id,borrow_id,status,reason) SELECT ?,?,'pending',? WHERE NOT EXISTS(SELECT 1 FROM violations WHERE borrow_id=?)",
                            userId, borrowId, "逾期 " + days + " 个自然日未归还（应还日期 " + dueDate + "）", borrowId);
                    if (inserted > 0) {
                        candidates++;
                        notifications.notifyLibrarians("违规待确认",
                                "借阅人（用户 #" + userId + "）所借《" + title + "》（" + code + "）逾期 " + days + " 天，已生成违规候选，请在图书管理中确认。",
                                "violation:" + borrowId);
                    }
                }
            }
        }
        LOG.info("图书扫描完成：到期提醒{}条，逾期提醒{}条，新增违规候选{}条，跳过{}条", dueReminders, overdueReminders, candidates, skipped);
        var result = new LinkedHashMap<String, Object>();
        result.put("date", today.toString());
        result.put("dueReminders", dueReminders);
        result.put("overdueReminders", overdueReminders);
        result.put("violationCandidates", candidates);
        result.put("skipped", skipped);
        return result;
    }
}