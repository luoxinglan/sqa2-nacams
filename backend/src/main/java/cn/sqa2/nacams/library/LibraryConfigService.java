package cn.sqa2.nacams.library;

import cn.sqa2.nacams.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class LibraryConfigService {
    public record Config(int maxBorrow, int loanDays, int remindBeforeWorkdays) {}
    private final Db db;
    private final Set<LocalDate> holidays;

    public LibraryConfigService(Db db, @Value("${app.library.holidays:}") String holidays) {
        this.db = db;
        var parsed = new HashSet<LocalDate>();
        for (String value : holidays.split(",")) {
            String text = value.trim();
            if (!text.isEmpty()) parsed.add(LocalDate.parse(text));
        }
        this.holidays = Set.copyOf(parsed);
    }

    public Config get() {
        var rows = db.jdbc.queryForList("SELECT max_borrow,loan_days,remind_before_workdays FROM library_config WHERE id=1");
        if (rows.isEmpty()) {
            db.jdbc.update("INSERT INTO library_config(id,max_borrow,loan_days,remind_before_workdays) VALUES(1,2,30,3)");
            return new Config(2, 30, 3);
        }
        var row = rows.get(0);
        return new Config(number(row.get("max_borrow"), "maxBorrow"), number(row.get("loan_days"), "loanDays"),
                number(row.get("remind_before_workdays"), "remindBeforeWorkdays"));
    }

    public Map<String, Object> view(Config config) {
        return new LinkedHashMap<>(Map.of("maxBorrow", config.maxBorrow(),
                "loanDays", config.loanDays(), "remindBeforeWorkdays", config.remindBeforeWorkdays()));
    }

    public Map<String, Object> update(Map<String, Object> body) {
        Config current = get();
        int maxBorrow = body.containsKey("maxBorrow") ? number(body.get("maxBorrow"), "maxBorrow") : current.maxBorrow();
        int loanDays = body.containsKey("loanDays") ? number(body.get("loanDays"), "loanDays") : current.loanDays();
        int remind = body.containsKey("remindBeforeWorkdays") ? number(body.get("remindBeforeWorkdays"), "remindBeforeWorkdays") : current.remindBeforeWorkdays();
        if (maxBorrow < 1 || maxBorrow > 20) throw new ApiException(422, "maxBorrow须为1至20的正整数");
        if (loanDays < 1 || loanDays > 365) throw new ApiException(422, "loanDays须为1至365的正整数");
        if (remind < 1 || remind > 30) throw new ApiException(422, "remindBeforeWorkdays须为1至30的正整数");
        db.jdbc.update("UPDATE library_config SET max_borrow=?,loan_days=?,remind_before_workdays=? WHERE id=1",
                maxBorrow, loanDays, remind);
        return view(new Config(maxBorrow, loanDays, remind));
    }

    public boolean isWorkday(LocalDate date) {
        var week = date.getDayOfWeek();
        return week != DayOfWeek.SATURDAY && week != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

    public LocalDate minusWorkdays(LocalDate date, int workdays) {
        LocalDate result = date;
        int remaining = workdays;
        while (remaining > 0) {
            result = result.minusDays(1);
            if (isWorkday(result)) remaining--;
        }
        return result;
    }

    public int workdaysBetween(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate d = from.plusDays(1); d.isBefore(to) || d.isEqual(to); d = d.plusDays(1)) {
            if (isWorkday(d)) count++;
        }
        return count;
    }

    private static int number(Object value, String field) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(Objects.toString(value, "").trim());
        } catch (NumberFormatException e) {
            throw new ApiException(422, field + "须为正整数");
        }
    }
}