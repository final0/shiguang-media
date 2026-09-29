package org.example.springaidemo.growth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/growth")
public class GrowthController {
    private final GrowthAuth auth;
    private final GrowthService service;
    private final GrowthMedia media;
    private final GrowthSharing sharing;
    private final GrowthProfile profile;
    private final GrowthAccountDeletion deletion;
    public GrowthController(GrowthAuth auth, GrowthService service, GrowthMedia media, GrowthSharing sharing, GrowthProfile profile, GrowthAccountDeletion deletion) {
        this.auth = auth; this.service = service; this.media = media; this.sharing = sharing;
        this.profile = profile;
        this.deletion = deletion;
    }
    private GrowthAuth.Actor actor(HttpServletRequest r) { return auth.actor(r.getSession(false)); }
    private void endSession(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) try { session.invalidate(); } catch (IllegalStateException ignored) { /* 并发请求已注销。 */ }
    }
    private Map<String,Object> signedIn(HttpServletRequest request, long id) {
        return signedIn(request, auth.authenticated(id));
    }
    private Map<String,Object> signedIn(HttpServletRequest request, GrowthAuth.Authenticated identity) {
        var session = request.getSession();
        request.changeSessionId(); // 登录切换身份时旋转会话 ID。
        session.setAttribute("growthUser", identity.id()); session.setAttribute("growthCredentialStamp", identity.credentialStamp()); session.setMaxInactiveInterval(8 * 60 * 60);
        return Map.of("ok", true);
    }
    @PostMapping("/auth/login") public Map<String,Object> login(@Valid @RequestBody GrowthDtos.Login input, HttpServletRequest r) { return signedIn(r, auth.login(input)); }
    @PostMapping("/auth/register") public Map<String,Object> register(@Valid @RequestBody GrowthDtos.Register input, HttpServletRequest r) { return signedIn(r, auth.register(input, GrowthReminderController.registrationContext(r,input.phone()))); }
    @PostMapping("/auth/demo") public Map<String,Object> demo(HttpServletRequest r) { return signedIn(r, auth.demo()); }
    @PostMapping("/auth/logout") public void logout(HttpServletRequest r) { endSession(r); }
    @PostMapping("/account/deletion") public Map<String,Object> deleteAccount(@Valid @RequestBody GrowthAccountDeletion.Apply input, HttpServletRequest r) {
        var result = deletion.apply(actor(r), input); endSession(r); return result;
    }
    @PostMapping("/auth/cancel-deletion") public void cancelDeletion(@Valid @RequestBody GrowthDtos.Login input) { deletion.cancel(input); }
    @PostMapping("/auth/rehome") public Map<String,Object> rehome(@Valid @RequestBody GrowthAccountDeletion.Rehome input, HttpServletRequest r) {
        deletion.rehome(input);
        return signedIn(r, auth.login(new GrowthDtos.Login(input.phone(), input.password())));
    }
    @GetMapping("/snapshot") public Map<String,Object> snapshot(HttpServletRequest r) { return service.snapshot(actor(r)); }
    @GetMapping("/account") public Map<String,Object> account(HttpServletRequest r) { return profile.get(actor(r)); }
    @PutMapping("/account") public void account(@Valid @RequestBody GrowthDtos.Profile input, HttpServletRequest r) { profile.update(actor(r), input); }
    @PutMapping("/account/phone") public void phone(@Valid @RequestBody GrowthDtos.Phone input, HttpServletRequest r) {
        auth.changePhone(actor(r), input); endSession(r);
    }
    @PutMapping("/account/password") public void password(@Valid @RequestBody GrowthDtos.Password input, HttpServletRequest r) {
        auth.changePassword(actor(r), input); endSession(r);
    }
    @PostMapping("/account/avatar") public void avatar(@RequestParam("file") MultipartFile file, HttpServletRequest r) { profile.upload(actor(r), file); }
    @DeleteMapping("/account/avatar") public void removeAvatar(HttpServletRequest r) { profile.removeAvatar(actor(r)); }
    @GetMapping("/users/{id}/avatar") public ResponseEntity<byte[]> avatar(@PathVariable long id, HttpServletRequest r) { return profile.avatar(actor(r), id); }
    @PostMapping("/children") public Map<String,Long> child(@Valid @RequestBody GrowthDtos.Child input, HttpServletRequest r) { return Map.of("id", service.addChild(actor(r), input)); }
    @DeleteMapping("/children/{id}") public void archiveChild(@PathVariable long id, HttpServletRequest r) { service.archiveChild(actor(r), id); }
    @PostMapping("/children/{id}/restore") public void restoreChild(@PathVariable long id, HttpServletRequest r) { service.restoreChild(actor(r), id); }
    @DeleteMapping("/children/{id}/purge") public void purgeChild(@PathVariable long id, HttpServletRequest r) { media.purgeChild(actor(r), id); }
    @PostMapping("/records") public Map<String,Long> create(@Valid @RequestBody GrowthDtos.Entry input, HttpServletRequest r) { return Map.of("id", service.saveRecord(actor(r), null, input)); }
    @PutMapping("/records/{id}") public Map<String,Long> edit(@PathVariable long id, @Valid @RequestBody GrowthDtos.Entry input, HttpServletRequest r) { return Map.of("id", service.saveRecord(actor(r), id, input)); }
    @DeleteMapping("/records/{id}") public void delete(@PathVariable long id, HttpServletRequest r) { service.delete(actor(r), id); }
    @PostMapping("/records/{id}/restore") public void restore(@PathVariable long id, HttpServletRequest r) { service.restore(actor(r), id); }
    @DeleteMapping("/records/{id}/purge") public void purge(@PathVariable long id, HttpServletRequest r) { media.purge(actor(r), id); }
    @PostMapping("/records/{id}/comments") public void comment(@PathVariable long id, @Valid @RequestBody GrowthDtos.Comment input, HttpServletRequest r) { service.comment(actor(r), id, input); }
    @PostMapping("/records/{id}/media") public void upload(@PathVariable long id, @RequestParam("files") List<MultipartFile> files, HttpServletRequest r) { media.upload(actor(r), id, files); }
    @GetMapping("/media/{id}") public void mediaFile(@PathVariable long id, HttpServletRequest r, HttpServletResponse response) throws java.io.IOException { media.read(actor(r), id, r, response); }
    @GetMapping("/media/{id}/download") public void downloadMedia(@PathVariable long id, HttpServletRequest r, HttpServletResponse response) throws java.io.IOException { media.download(actor(r), id, r, response); }
    @PostMapping("/measurements") public void measurement(@Valid @RequestBody GrowthDtos.Measurement input, HttpServletRequest r) { service.saveMeasurement(actor(r), null, input); }
    @PutMapping("/measurements/{id}") public void editMeasurement(@PathVariable long id, @Valid @RequestBody GrowthDtos.Measurement input, HttpServletRequest r) { service.saveMeasurement(actor(r), id, input); }
    @DeleteMapping("/measurements/{id}") public void deleteMeasurement(@PathVariable long id, HttpServletRequest r) { service.deleteMeasurement(actor(r), id); }
    @PostMapping("/vaccines") public void vaccine(@Valid @RequestBody GrowthDtos.Vaccine input, HttpServletRequest r) { service.saveVaccine(actor(r), null, input); }
    @PutMapping("/vaccines/{id}") public void editVaccine(@PathVariable long id, @Valid @RequestBody GrowthDtos.Vaccine input, HttpServletRequest r) { service.saveVaccine(actor(r), id, input); }
    @DeleteMapping("/vaccines/{id}") public void deleteVaccine(@PathVariable long id, HttpServletRequest r) { service.deleteVaccine(actor(r), id); }
    @PostMapping("/family/invite") public Map<String,Object> invite(HttpServletRequest r) { return service.invite(actor(r)); }
    @PutMapping("/family") public void settings(@Valid @RequestBody GrowthDtos.Settings input, HttpServletRequest r) { service.settings(actor(r), input); }
    @DeleteMapping("/family/members/{id}") public void remove(@PathVariable long id, HttpServletRequest r) { service.removeMember(actor(r), id); }
    @PutMapping("/family/members/{id}/role") public void memberRole(@PathVariable long id, @Valid @RequestBody GrowthDtos.MemberRole input, HttpServletRequest r) { service.memberRole(actor(r), id, input); }
    @PostMapping("/notices/read") public void read(HttpServletRequest r) { service.readNotices(actor(r)); }
    @GetMapping("/children/{id}/export") public ResponseEntity<?> export(@PathVariable long id, HttpServletRequest r) { return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=child-" + id + ".json").body(service.export(actor(r), id)); }
    @PostMapping("/records/{id}/shares") public Map<String,Object> share(@PathVariable long id, @Valid @RequestBody GrowthDtos.Share input, HttpServletRequest r) { return sharing.create(actor(r), id, input); }
    @DeleteMapping("/shares/{id}") public void revoke(@PathVariable long id, HttpServletRequest r) { sharing.revoke(actor(r), id); }
    @GetMapping("/public/{token}") public Map<String,Object> view(@PathVariable String token, HttpServletRequest r) { return sharing.view(token, r.getSession()); }
    @GetMapping("/public/{token}/media/{id}") public ResponseEntity<Resource> sharedImage(@PathVariable String token, @PathVariable long id, HttpServletRequest r) { return sharing.image(token, id, r.getSession()); }
}
