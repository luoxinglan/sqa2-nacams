package cn.sqa2.nacams.library;

import cn.sqa2.nacams.common.Db;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class HelpService {
    private record Entry(String category, String title, String body) {}
    private static final List<Entry> BUILT_IN = List.of(
            new Entry("图书借阅", "如何借阅图书", "在图书目录检索图书编号或书名，确认状态为“可借”后提交借阅申请；图书管理教师审核通过后即可到研究所文件柜取书。每人同时在借不超过 2 册，借期 30 天。"),
            new Entry("图书借阅", "归还与逾期", "请在应还日期前归还。归还后进入 5 个自然日冷却期，冷却期内同一册图书不可再借。逾期未还将收到站内提醒；逾期满 30 个自然日将生成诚信违规候选，经图书管理教师确认后计入违规次数。"),
            new Entry("图书借阅", "停止借阅资格", "已确认违规超过 3 次（即第 4 次起）将停止借阅资格。可在“我的借阅—诚信记录”查看本人违规次数与原因。"),
            new Entry("资料下载", "下载专区栏目", "下载专区提供开题、中期、答辩、学位论文、专利、会议、上级文件、其他共 8 类文件。登录后按分类浏览并下载，文件仅对已登录用户开放。"),
            new Entry("账号与档案", "照片上传要求", "本人照片须为 48mm×33mm 护照照片比例（宽高比 33:48），文件不超过 2MB。上传前可在“照片检测”中预检，检测仅提供建议，不阻断保存。"),
            new Entry("账号与档案", "档案字段校验", "工号、学号、姓名、移动电话、办公电话等字段由服务端按规则校验；提交失败时按提示修改对应字段即可。留学生姓名可申请豁免标记。")
    );
    private final Db db;
    public HelpService(Db db) { this.db = db; }

    public Map<String, Object> search(String keyword) {
        String query = keyword == null ? "" : keyword.trim();
        var merged = new LinkedHashMap<String, Entry>();
        for (var row : db.jdbc.queryForList("SELECT category,title,body FROM help_entries ORDER BY sort_order,id")) {
            var entry = new Entry(Db.str(row, "category"), Db.str(row, "title"), Objects.toString(row.get("body"), ""));
            merged.put(entry.category() + "\u0000" + entry.title(), entry);
        }
        for (var entry : BUILT_IN) merged.putIfAbsent(entry.category() + "\u0000" + entry.title(), entry);
        var tree = new LinkedHashMap<String, List<Map<String, Object>>>();
        var matches = new ArrayList<Map<String, Object>>();
        for (var entry : merged.values()) {
            var item = new LinkedHashMap<String, Object>();
            item.put("title", entry.title());
            item.put("body", entry.body());
            item.put("category", entry.category());
            tree.computeIfAbsent(entry.category(), key -> new ArrayList<>()).add(item);
            if (!query.isEmpty() && (entry.title().contains(query) || entry.body().contains(query))) matches.add(item);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("tree", tree.entrySet().stream().map(e -> Map.of("category", e.getKey(), "items", e.getValue())).toList());
        result.put("matches", matches);
        result.put("keyword", query);
        return result;
    }
}