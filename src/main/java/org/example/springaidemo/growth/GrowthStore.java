package org.example.springaidemo.growth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.util.*;

/** SQL 辅助层。业务查询必须显式携带 Actor.familyId，不接收客户端家庭 ID。 */
@Repository
public class GrowthStore {
    private final JdbcTemplate jdbc;
    public GrowthStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /** 通过可代理的方法访问目标对象中的依赖，不允许业务层直接读取代理对象字段。 */
    public long currentTimeMillis() {
        var timestamp = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", java.sql.Timestamp.class);
        return Objects.requireNonNull(timestamp, "Database current timestamp must not be null").getTime();
    }
    /** 提醒计划使用 epoch 毫秒；直接读取数值，避免 UTC 数据库时间被 JDBC 按北京时间解释。 */
    public long currentEpochMillis() {
        Long epoch = jdbc.queryForObject("SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED)", Long.class);
        return Objects.requireNonNull(epoch, "Database epoch must not be null");
    }
    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public long insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    public List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            row.forEach((name, value) -> {
                String[] words = name.toLowerCase(Locale.ROOT).split("_");
                StringBuilder camel = new StringBuilder(words[0]);
                for (int i = 1; i < words.length; i++) camel.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
                if (value instanceof java.sql.Timestamp timestamp) value = timestamp.toLocalDateTime().toString();
                if (value instanceof java.sql.Date date) value = date.toLocalDate().toString();
                // MySQL BOOLEAN 是 TINYINT(1)，不同 Connector/J 配置可能返回 Number。
                if (name.toLowerCase(Locale.ROOT).startsWith("is_") && value instanceof Number flag) {
                    value = flag.intValue() != 0;
                }
                result.put(camel.toString(), value);
            });
            return result;
        }).toList();
    }
    public Map<String, Object> one(String sql, Object... args) {
        var rows = list(sql, args);
        if (rows.isEmpty()) throw GrowthException.missing();
        return rows.get(0);
    }
    public long number(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
    public void log(GrowthAuth.Actor actor, String action, String detail) {
        update("INSERT INTO operate_log(family_id,user_id,action_type,detail) VALUES(?,?,?,?)", actor.familyId(), actor.id(), action, detail);
    }
    /** 调用方须已持有家庭行锁，或正在创建尚未提交、其他请求不可见的新家庭。 */
    public void ensureTag(long familyId, String name) {
        if (number("SELECT COUNT(*) FROM tag WHERE family_id=? AND name=?", familyId, name) == 0) {
            update("INSERT INTO tag(family_id,name) VALUES(?,?)", familyId, name);
        }
    }
}
