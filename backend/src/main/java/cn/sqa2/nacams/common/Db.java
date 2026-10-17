package cn.sqa2.nacams.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import java.sql.Statement;
import java.util.*;

@Component
public class Db {
    public final JdbcTemplate jdbc;
    public Db(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<String,Object> one(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new ApiException(404, "记录不存在");
        return new LinkedHashMap<>(rows.get(0));
    }
    public long insert(String sql, Object... args) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, holder);
        return Objects.requireNonNull(holder.getKey()).longValue();
    }
    public static long id(Object value) { return ((Number)value).longValue(); }
    public static boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0;
    }
    public static String str(Map<String,?> value, String key) {
        return Objects.toString(value.get(key), "").trim();
    }
    public static int page(int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 200) throw new ApiException(400, "分页范围错误，page_size为1至200");
        return (page - 1) * size;
    }
    public Map<String,Object> page(String fromWhere, List<Object> args, String order, int page, int size) {
        int offset = page(page, size);
        long total = jdbc.queryForObject("SELECT COUNT(*) " + fromWhere, Long.class, args.toArray());
        var values = new ArrayList<>(args); values.add(size); values.add(offset);
        var items = jdbc.queryForList("SELECT * " + fromWhere + " ORDER BY " + order + " LIMIT ? OFFSET ?", values.toArray());
        return new LinkedHashMap<>(Map.of("items", items, "total", total, "page", page, "page_size", size));
    }
}
