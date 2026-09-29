package org.example.springaidemo.growth;

import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.example.springaidemo.growth.GrowthAuth.Actor;

@Service
public class GrowthAssistantContext {
    private final GrowthStore db;
    private final GrowthService service;
    private final JsonMapper json = JsonMapper.builder().build();
    public GrowthAssistantContext(GrowthStore db, GrowthService service) { this.db = db; this.service = service; }

    public record Context(String promptData, String fingerprint, Map<String,Object> facts, List<Map<String,Object>> sources, boolean empty) { }
    public Context prepare(Actor a, GrowthAssistantController.Ask input) {
        if (input.to().isBefore(input.from()) || ChronoUnit.DAYS.between(input.from(), input.to()) > 366) throw GrowthException.bad("请选择不超过一年的有效日期范围");
        var child = service.child(a, input.childId());
        Map<String,Object> identity = new LinkedHashMap<>();
        identity.put("nickname", child.get("name"));
        identity.put("ageMonths", ChronoUnit.MONTHS.between(LocalDate.parse(child.get("birthday").toString()), LocalDate.now()));
        identity.put("from", input.from().toString()); identity.put("to", input.to().toString());
        if (input.task().equals("chat")) {
            // 普通聊天只使用基本档案；数据库中的记录、相册和测量需在专门任务中授权使用。
            return new Context(json.writeValueAsString(identity), fingerprint(identity), Map.of(), List.of(), false);
        }
        if (input.task().equals("diary")) {
            identity.put("userNotes", input.question());
            return new Context(json.writeValueAsString(identity), fingerprint(identity), Map.of(), List.of(), false);
        }
        // 有上限且明确拒绝截断：统计覆盖选定范围中的每一条测量。
        var rows = db.list("SELECT id,measure_time,age_month,height,weight,head FROM grow_data WHERE family_id=? AND child_id=? AND measure_time>=? AND measure_time<=? ORDER BY measure_time,id LIMIT 5001",
                a.familyId(), input.childId(), input.from(), input.to());
        if (rows.size() > 5000) throw GrowthException.bad("这个范围内测量记录过多，请缩短日期范围");
        Map<String,Object> facts = new LinkedHashMap<>();
        facts.put("count", rows.size()); facts.put("from", input.from().toString()); facts.put("to", input.to().toString());
        List<Map<String,Object>> metrics = new ArrayList<>();
        for (String key : List.of("height", "weight", "head")) {
            var measured = rows.stream().filter(row -> row.get(key) != null).toList();
            if (measured.isEmpty()) continue;
            var first = measured.get(0); var last = measured.get(measured.size() - 1);
            var start = new BigDecimal(first.get(key).toString()); var end = new BigDecimal(last.get(key).toString());
            Map<String,Object> metric = new LinkedHashMap<>();
            metric.put("key", key); metric.put("label", switch (key) { case "height" -> "身高"; case "weight" -> "体重"; default -> "头围"; });
            metric.put("unit", key.equals("weight") ? "kg" : "cm"); metric.put("count", measured.size());
            metric.put("firstDate", first.get("measureTime")); metric.put("lastDate", last.get("measureTime"));
            metric.put("first", start); metric.put("last", end);
            // 单条记录没有跨次变化，不能把 0 当成“没有长高”。
            metric.put("change", measured.size() > 1 ? end.subtract(start) : null);
            metrics.add(metric);
        }
        facts.put("metrics", metrics);
        List<Map<String,Object>> sources = rows.stream().skip(Math.max(0, rows.size() - 12)).toList();
        identity.put("statistics", facts); identity.put("latestMeasurements", sources);
        identity.put("sourceNote", "统计涵盖范围内全部测量；明细只展示最近12条。没有生长标准或医学结论。");
        String version = fingerprint(List.of(child, rows));
        return new Context(json.writeValueAsString(identity), version, facts, sources, rows.isEmpty());
    }
    public boolean unchanged(Actor a, GrowthAssistantController.Ask input, Context context) {
        return prepare(a, input).fingerprint().equals(context.fingerprint());
    }
    private String fingerprint(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
