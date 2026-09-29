package org.example.springaidemo.growth;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class GrowthReminders {
    public record Input(@NotBlank @Size(max=80) String title, @Size(max=1000) String content,
                        @Positive Long childId, @NotNull LocalDateTime time,
                        @NotBlank @Pattern(regexp="ONCE|DAILY|WEEKLY") String cadence) { }
    public record State(@NotBlank @Pattern(regexp="PAUSED|COMPLETE") String state) { }
    private static final Logger log=LoggerFactory.getLogger(GrowthReminders.class);
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private final GrowthStore db;
    private final GrowthService service;
    private final GrowthMail mail;
    private final TransactionTemplate tx;
    private long wakeCursor;
    private long lastScanLogNanos;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"growth-reminder-scan");t.setDaemon(true);return t;});
    // 邮件网络请求不占用调度线程或数据库事务；有界队列提供背压。
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"growth-mail-worker");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public GrowthReminders(GrowthStore db,GrowthService service,GrowthMail mail,PlatformTransactionManager manager){this.db=db;this.service=service;this.mail=mail;this.tx=new TransactionTemplate(manager);}
    @PostConstruct public void start(){
        scheduler.scheduleWithFixedDelay(this::scan,30,30,TimeUnit.SECONDS);
        log.info("Reminder scheduler started, intervalSeconds=30, mailConfigured={}",mail.configured());
    }
    @PreDestroy public void close(){scheduler.shutdownNow();workers.shutdownNow();}
    private long number(Map<String,Object> r,String key){return ((Number)r.get(key)).longValue();}
    private long now(){return db.currentEpochMillis();}
    private boolean flag(Object value){return Boolean.TRUE.equals(value)||(value instanceof Number n&&n.intValue()!=0);}
    private long period(Map<String,Object> r){return "DAILY".equals(r.get("cadence"))?86400000L:"WEEKLY".equals(r.get("cadence"))?604800000L:0;}
    private long nextFuture(Map<String,Object> r,long clock){long start=number(r,"startMs"),period=period(r);return start>clock?start:period==0?start:start+((clock-start)/period+1)*period;}
    public Map<String,Object> list(GrowthAuth.Actor a){
        var result=new LinkedHashMap<String,Object>();
        result.put("account",db.one("SELECT email,email_verified_at,email_enabled FROM app_user WHERE id=?",a.id()));
        result.put("items",db.list("SELECT r.*,c.name AS child_name,(SELECT d.status FROM reminder_delivery d WHERE d.reminder_id=r.id ORDER BY d.id DESC LIMIT 1) AS delivery_status FROM growth_reminder r LEFT JOIN child c ON c.id=r.child_id AND c.family_id=r.family_id WHERE r.family_id=? AND r.user_id=? ORDER BY r.id DESC",a.familyId(),a.id()));
        result.put("mailConfigured",mail.configured());return result;
    }
    private Map<String,Object> own(GrowthAuth.Actor a,long id){return db.one("SELECT * FROM growth_reminder WHERE id=? AND family_id=? AND user_id=? FOR UPDATE",id,a.familyId(),a.id());}
    @Transactional public long save(GrowthAuth.Actor a,Long id,Input input){
        service.lockFamily(a);
        var user=db.one("SELECT email,email_verified_at,email_enabled,phone FROM app_user WHERE id=? AND is_deleted=FALSE",a.id());
        if(user.get("phone").toString().startsWith("demo-"))throw GrowthException.bad("示例账号不支持邮件提醒，请注册正式账号");
        if(user.get("email")==null||user.get("emailVerifiedAt")==null)throw GrowthException.bad("请先在个人资料中绑定并验证QQ邮箱");
        if(!flag(user.get("emailEnabled")))throw GrowthException.bad("请先在个人资料中开启邮件提醒");
        if(input.childId()!=null)service.child(a,input.childId());
        long start=input.time().atZone(ZONE).toInstant().toEpochMilli();
        if(start<=now())throw GrowthException.bad("提醒时间必须晚于当前北京时间");
        if(id==null){
            if(db.number("SELECT COUNT(*) FROM growth_reminder WHERE user_id=?",a.id())>=100)throw GrowthException.bad("最多保留100条提醒，请先删除不需要的提醒");
            return db.insert("INSERT INTO growth_reminder(family_id,user_id,child_id,title,content,cadence,start_ms,next_ms) VALUES(?,?,?,?,?,?,?,?)",a.familyId(),a.id(),input.childId(),input.title().trim(),input.content()==null?"":input.content().trim(),input.cadence(),start,start);
        }
        own(a,id);cancelQueued(id,"EDITED");
        db.update("UPDATE growth_reminder SET child_id=?,title=?,content=?,cadence=?,start_ms=?,next_ms=?,state='ACTIVE',revision=revision+1 WHERE id=?",input.childId(),input.title().trim(),input.content()==null?"":input.content().trim(),input.cadence(),start,start,id);return id;
    }
    private void cancelQueued(long id,String reason){db.update("UPDATE reminder_delivery SET status='CANCELLED',result_code=? WHERE reminder_id=? AND status IN ('QUEUED','SENDING')",reason,id);}
    @Transactional public void state(GrowthAuth.Actor a,long id,String state){service.lockFamily(a);own(a,id);cancelQueued(id,state);db.update("UPDATE growth_reminder SET state=?,revision=revision+1 WHERE id=?",state,id);}
    @Transactional public void delete(GrowthAuth.Actor a,long id){service.lockFamily(a);own(a,id);GrowthReminderLifecycle.purge(db,"id=?",id);}
    public List<Map<String,Object>> history(GrowthAuth.Actor a,long id){
        db.one("SELECT id FROM growth_reminder WHERE id=? AND family_id=? AND user_id=?",id,a.familyId(),a.id());
        return db.list("SELECT scheduled_ms,status,attempts,result_code,sent_ms FROM reminder_delivery WHERE reminder_id=? ORDER BY id DESC LIMIT 30",id);
    }
    private boolean eligible(Map<String,Object> r){
        long family=number(r,"familyId"),user=number(r,"userId");
        if(db.number("SELECT COUNT(*) FROM family_member m JOIN app_user u ON u.id=m.user_id WHERE m.family_id=? AND m.user_id=? AND u.is_deleted=FALSE AND u.email IS NOT NULL AND u.email_verified_at IS NOT NULL AND u.email_enabled=TRUE",family,user)==0)return false;
        if(r.get("childId")!=null&&db.number("SELECT COUNT(*) FROM child WHERE id=? AND family_id=? AND is_active=TRUE",r.get("childId"),family)==0)return false;
        return db.number("SELECT COUNT(*) FROM account_deletion WHERE user_id=? OR (family_id=? AND scope='FAMILY')",user,family)==0;
    }
    private void prepare(long id,long family){tx.executeWithoutResult(status->{
        if(db.list("SELECT id FROM family WHERE id=? FOR UPDATE",family).isEmpty())return;
        var rows=db.list("SELECT * FROM growth_reminder WHERE id=? FOR UPDATE",id);if(rows.isEmpty())return;
        var r=rows.get(0);long clock=now();String state=r.get("state").toString();
        if(!eligible(r)){
            if("ACTIVE".equals(state))GrowthReminderLifecycle.suspend(db,"id=?",id);
            return;
        }
        if("SUSPENDED".equals(state)){
            long next=nextFuture(r,clock);
            db.update("UPDATE growth_reminder SET state=?,next_ms=?,revision=revision+1 WHERE id=?",next<=clock?"PAUSED":"ACTIVE",next,id);return;
        }
        if(!"ACTIVE".equals(state)||r.get("nextMs")==null||number(r,"nextMs")>clock)return;
        long due=number(r,"nextMs"),period=period(r);
        if(period>0)due+=((clock-due)/period)*period; // 周期任务只补最近的一次。
        long version=db.number("SELECT email_version FROM app_user WHERE id=?",r.get("userId"));
        db.update("INSERT INTO reminder_delivery(reminder_id,revision,scheduled_ms,email_version,next_attempt_ms) VALUES(?,?,?,?,?)",id,r.get("revision"),due,version,clock);
        db.update("UPDATE growth_reminder SET next_ms=? WHERE id=?",period==0?null:due+period,id);
    });}
    private record Message(long id,long reminder,long revision,String token,String to,String title,String content,long scheduledMs,boolean delayed,int attempts){ }
    private Message claim(long delivery,long family){return tx.execute(status->{
        if(db.list("SELECT id FROM family WHERE id=? FOR UPDATE",family).isEmpty())return null;
        var rows=db.list("SELECT d.*,r.family_id,r.user_id,r.child_id,r.title,r.content,r.cadence,r.start_ms,r.state,r.revision AS current_revision FROM reminder_delivery d JOIN growth_reminder r ON r.id=d.reminder_id WHERE d.id=? FOR UPDATE",delivery);
        if(rows.isEmpty())return null;var r=rows.get(0);long clock=now();String state=r.get("status").toString();
        if(!(("QUEUED".equals(state)&&number(r,"nextAttemptMs")<=clock)||("SENDING".equals(state)&&r.get("leaseMs")!=null&&number(r,"leaseMs")<=clock)))return null;
        var user=db.one("SELECT email,email_version FROM app_user WHERE id=?",r.get("userId"));
        if(!"ACTIVE".equals(r.get("state"))||number(r,"revision")!=number(r,"currentRevision")||number(r,"emailVersion")!=number(user,"emailVersion")||!eligible(r)){
            db.update("UPDATE reminder_delivery SET status='CANCELLED',result_code='NO_LONGER_ELIGIBLE' WHERE id=?",delivery);return null;
        }
        int attempts=((Number)r.get("attempts")).intValue()+1;
        if(attempts>3){
            db.update("UPDATE reminder_delivery SET status='FAILED',result_code='RETRY_LIMIT' WHERE id=?",delivery);
            db.update("UPDATE growth_reminder SET state='FAILED' WHERE id=? AND revision=? AND cadence='ONCE' AND state='ACTIVE'",r.get("reminderId"),r.get("revision"));return null;
        }
        String token=UUID.randomUUID().toString();
        db.update("UPDATE reminder_delivery SET status='SENDING',attempts=?,claim_token=?,lease_ms=? WHERE id=?",attempts,token,clock+120000,delivery);
        long scheduledMs=number(r,"scheduledMs");
        return new Message(delivery,number(r,"reminderId"),number(r,"revision"),token,user.get("email").toString(),r.get("title").toString(),Objects.toString(r.get("content"),""),scheduledMs,clock-scheduledMs>120000,attempts);
    });}
    private void deliver(long id,long family){
        try{
            Message m=claim(id,family);if(m==null)return;
            String result="SENT",code="SMTP_ACCEPTED";
            try{mail.sendReminder(m.to(),m.title(),m.content(),m.scheduledMs(),m.delayed());}
            catch(MailAuthenticationException e){result="FAILED";code="SMTP_AUTH_FAILED";}
            catch(RuntimeException e){result=m.attempts()>=3?"FAILED":"QUEUED";code="SMTP_UNAVAILABLE";}
            // 只记录编号及固定结果码，不输出收件地址、邮件内容或 SMTP 异常消息。
            if("SENT".equals(result))log.info("Reminder SMTP accepted, reminderId={}, deliveryId={}, attempt={}",m.reminder(),m.id(),m.attempts());
            else log.warn("Reminder SMTP failed, reminderId={}, deliveryId={}, attempt={}, result={}",m.reminder(),m.id(),m.attempts(),code);
            String finalResult=result,finalCode=code;
            tx.executeWithoutResult(status->{
                if(db.list("SELECT id FROM family WHERE id=? FOR UPDATE",family).isEmpty())return;
                long clock=now();
                int updated=db.update("UPDATE reminder_delivery SET status=?,result_code=?,sent_ms=?,next_attempt_ms=?,lease_ms=NULL WHERE id=? AND status='SENDING' AND claim_token=?",finalResult,finalCode,"SENT".equals(finalResult)?clock:null,clock+(m.attempts()==1?60000:300000),m.id(),m.token());
                if(updated>0&&!"QUEUED".equals(finalResult))db.update("UPDATE growth_reminder SET state=? WHERE id=? AND revision=? AND cadence='ONCE' AND state='ACTIVE'","SENT".equals(finalResult)?"COMPLETE":"FAILED",m.reminder(),m.revision());
            });
        }catch(RuntimeException e){log.warn("Reminder delivery deferred, deliveryId={}, type={}",id,e.getClass().getSimpleName());}
    }
    public void scan(){
        try{
            long tick=System.nanoTime();
            boolean heartbeat=lastScanLogNanos==0||tick-lastScanLogNanos>=TimeUnit.MINUTES.toNanos(5);
            if(!mail.configured()){
                if(heartbeat){log.warn("Reminder scan skipped: mail service is not configured");lastScanLogNanos=tick;}
                return;
            }
            long clock=now();
            var due=db.list("SELECT id,family_id FROM growth_reminder WHERE state='ACTIVE' AND next_ms<=? ORDER BY next_ms,id LIMIT 100",clock);
            for(var row:due){try{prepare(number(row,"id"),number(row,"familyId"));}catch(RuntimeException e){log.warn("Reminder preparation deferred, reminderId={}, type={}",row.get("id"),e.getClass().getSimpleName());}}
            // 按主键轮询自动暂停的任务，避免长期停用的前100条阻塞后续提醒。
            var sleeping=db.list("SELECT id,family_id FROM growth_reminder WHERE state='SUSPENDED' AND id>? ORDER BY id LIMIT 50",wakeCursor);
            if(sleeping.isEmpty())wakeCursor=0;
            for(var row:sleeping){wakeCursor=number(row,"id");try{prepare(wakeCursor,number(row,"familyId"));}catch(RuntimeException e){log.warn("Reminder resume deferred, reminderId={}",wakeCursor);}}
            // prepare 写入的时间可能晚于本轮扫描开始，取新时间让刚入队的邮件本轮即可领取。
            long deliveryClock=now();
            var deliveries=db.list("SELECT d.id,r.family_id FROM reminder_delivery d JOIN growth_reminder r ON r.id=d.reminder_id WHERE (d.status='QUEUED' AND d.next_attempt_ms<=?) OR (d.status='SENDING' AND d.lease_ms<=?) ORDER BY d.id LIMIT 30",deliveryClock,deliveryClock);
            if(heartbeat||!due.isEmpty()||!deliveries.isEmpty()){
                log.info("Reminder scan, databaseTime={}, applicationTime={}, dueCount={}, deliveryCount={}",Instant.ofEpochMilli(clock).atZone(ZONE),Instant.now().atZone(ZONE),due.size(),deliveries.size());
                lastScanLogNanos=tick;
            }
            for(var row:deliveries){try{workers.execute(()->deliver(number(row,"id"),number(row,"familyId")));}catch(RejectedExecutionException full){break;}}
        }catch(RuntimeException e){log.warn("Reminder scan deferred, type={}",e.getClass().getSimpleName());}
    }
}
