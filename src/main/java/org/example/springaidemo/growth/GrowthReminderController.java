package org.example.springaidemo.growth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/growth")
public class GrowthReminderController {
    private final GrowthAuth auth;
    private final GrowthStore db;
    private final GrowthEmailVerification codes;
    private final GrowthEmailAccount account;
    private final GrowthReminders reminders;
    private final GrowthAccountDeletion deletion;
    public GrowthReminderController(GrowthAuth auth,GrowthStore db,GrowthEmailVerification codes,GrowthEmailAccount account,GrowthReminders reminders,GrowthAccountDeletion deletion){this.auth=auth;this.db=db;this.codes=codes;this.account=account;this.reminders=reminders;this.deletion=deletion;}
    private GrowthAuth.Actor actor(HttpServletRequest r){return auth.actor(r.getSession(false));}
    static String registrationContext(HttpServletRequest r,String phone){return "register:"+r.getSession().getId()+":"+phone;}
    private String bindingContext(HttpServletRequest r,GrowthAuth.Actor a){return "bind:"+a.id()+":"+r.getSession().getId();}
    @PostMapping("/auth/email-code") public void registrationCode(@Valid @RequestBody GrowthEmailAccount.Code input,HttpServletRequest r){
        if(input.phone()==null)throw GrowthException.bad("请先填写注册手机号");
        deletion.guardPhone(input.phone());
        if(db.number("SELECT COUNT(*) FROM app_user WHERE phone=?",input.phone())>0)throw GrowthException.bad("该手机号已注册，请直接登录");
        String email=codes.normalize(input.email());account.available(email);
        codes.send(email,registrationContext(r,input.phone()),r.getRemoteAddr());
    }
    @GetMapping("/account/email") public Map<String,Object> email(HttpServletRequest r){return account.get(actor(r));}
    @PostMapping("/account/email-code") public void bindingCode(@Valid @RequestBody GrowthEmailAccount.Code input,HttpServletRequest r){
        var a=actor(r);
        if(db.number("SELECT COUNT(*) FROM app_user WHERE id=? AND phone LIKE 'demo-%'",a.id())>0)throw GrowthException.bad("示例账号不支持邮件功能");
        String email=codes.normalize(input.email());account.available(email);codes.send(email,bindingContext(r,a),r.getRemoteAddr());
    }
    @PutMapping("/account/email") public void bind(@Valid @RequestBody GrowthEmailAccount.Bind input,HttpServletRequest r){var a=actor(r);account.bind(a,input,bindingContext(r,a));}
    @PutMapping("/account/email-preference") public void preference(@Valid @RequestBody GrowthEmailAccount.Preference input,HttpServletRequest r){account.preference(actor(r),input.enabled());}
    @GetMapping("/reminders") public Map<String,Object> reminders(HttpServletRequest r){return reminders.list(actor(r));}
    @PostMapping("/reminders") public Map<String,Long> create(@Valid @RequestBody GrowthReminders.Input input,HttpServletRequest r){return Map.of("id",reminders.save(actor(r),null,input));}
    @PutMapping("/reminders/{id}") public void update(@PathVariable long id,@Valid @RequestBody GrowthReminders.Input input,HttpServletRequest r){reminders.save(actor(r),id,input);}
    @PutMapping("/reminders/{id}/state") public void state(@PathVariable long id,@Valid @RequestBody GrowthReminders.State input,HttpServletRequest r){reminders.state(actor(r),id,input.state());}
    @DeleteMapping("/reminders/{id}") public void delete(@PathVariable long id,HttpServletRequest r){reminders.delete(actor(r),id);}
    @GetMapping("/reminders/{id}/history") public List<Map<String,Object>> history(@PathVariable long id,HttpServletRequest r){return reminders.history(actor(r),id);}
}
