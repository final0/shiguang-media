package org.example.springaidemo.growth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Redis 原子限流、尝试次数与一次性校验。Redis 故障时拒绝验证，不降级放行。 */
@Service
public class GrowthEmailVerification {
    private final StringRedisTemplate redis;
    private final GrowthMail mail;
    private final SecureRandom random = new SecureRandom();
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>("""
        if redis.call('EXISTS',KEYS[1])==1 then return 0 end
        for i=2,4 do
          local n=tonumber(redis.call('GET',KEYS[i]) or '0')
          if n>=tonumber(ARGV[i-1]) then return 0 end
        end
        redis.call('SET',KEYS[1],'1','EX',60)
        for i=2,4 do
          if redis.call('INCR',KEYS[i])==1 then redis.call('EXPIRE',KEYS[i],3600) end
        end
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>("""
        redis.call('HSET',KEYS[1],'salt',ARGV[1],'hash',ARGV[2],'attempts','0','ready','0')
        redis.call('EXPIRE',KEYS[1],300)
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> READY = new DefaultRedisScript<>("""
        if redis.call('HGET',KEYS[1],'hash')~=ARGV[1] then return 0 end
        if ARGV[2]=='delete' then redis.call('DEL',KEYS[1]) else redis.call('HSET',KEYS[1],'ready','1') end
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> VERIFY = new DefaultRedisScript<>("""
        if redis.call('HGET',KEYS[1],'ready')~='1' then return 0 end
        if redis.call('HGET',KEYS[1],'hash')==ARGV[1] then redis.call('DEL',KEYS[1]); return 1 end
        if redis.call('HINCRBY',KEYS[1],'attempts',1)>=5 then redis.call('DEL',KEYS[1]) end
        return 0
        """, Long.class);
    public GrowthEmailVerification(StringRedisTemplate redis, GrowthMail mail) { this.redis=redis; this.mail=mail; }
    public String normalize(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (value.length()>100 || !value.matches("[a-z0-9][a-z0-9._-]{0,63}@qq\\.com")) throw GrowthException.bad("请输入有效的 QQ 邮箱（@qq.com）");
        return value;
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String key(String email, String context) { return "shiguang:email:code:" + hash(context+":"+email); }
    private GrowthException unavailable() { return new GrowthException(HttpStatus.SERVICE_UNAVAILABLE,"验证码服务暂不可用，请稍后重试"); }
    public void send(String input, String context, String ip) {
        String email=normalize(input), key=key(email,context), digest=hash(email);
        if (!mail.configured()) throw new GrowthException(HttpStatus.SERVICE_UNAVAILABLE,"邮件服务尚未配置，请联系管理员");
        try {
            Long result=redis.execute(LIMIT,List.of("shiguang:email:cooldown:"+digest,"shiguang:email:hour:"+digest,
                    "shiguang:email:ip:"+hash(ip),"shiguang:email:global-hour"),"10","100","200");
            if (!Long.valueOf(1).equals(result)) throw new GrowthException(HttpStatus.TOO_MANY_REQUESTS,"发送过于频繁，请至少间隔60秒；超过每小时限额后需稍后再试");
        } catch (DataAccessException e) { throw unavailable(); }
        String code=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
        String salt=UUID.randomUUID().toString(), codeHash=hash(salt+":"+code);
        try { redis.execute(SAVE,List.of(key),salt,codeHash); }
        catch (DataAccessException e) { throw unavailable(); }
        try {
            mail.sendVerification(email,code);
            if (!Long.valueOf(1).equals(redis.execute(READY,List.of(key),codeHash,"ready"))) throw unavailable();
        } catch (RuntimeException e) {
            try { redis.execute(READY,List.of(key),codeHash,"delete"); } catch (RuntimeException ignored) { /* TTL 兜底，未就绪的验证码不能使用。 */ }
            throw new GrowthException(HttpStatus.SERVICE_UNAVAILABLE,"验证码发送未完成，请稍后重新获取；请管理员核对 SMTP 配置及发信限制");
        }
    }
    public String consume(String input, String code, String context) {
        String email=normalize(input), key=key(email,context);
        if (code==null || !code.matches("[0-9]{6}")) throw GrowthException.bad("请输入6位数字验证码");
        try {
            Object salt=redis.opsForHash().get(key,"salt");
            if (salt==null || !Long.valueOf(1).equals(redis.execute(VERIFY,List.of(key),hash(salt+":"+code))))
                throw GrowthException.bad("验证码错误、已使用或已过期；连续错误5次需重新获取");
        } catch (DataAccessException e) { throw unavailable(); }
        return email;
    }
}
