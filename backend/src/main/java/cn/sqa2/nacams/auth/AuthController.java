package cn.sqa2.nacams.auth;

import cn.sqa2.nacams.common.*;
import cn.sqa2.nacams.admin.UserService;
import cn.sqa2.nacams.portal.MediaService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final Access access;private final AuthService auth;private final UserService users;private final MediaService media;
    public AuthController(Access access,AuthService auth,UserService users,MediaService media){this.access=access;this.auth=auth;this.users=users;this.media=media;}
    @PostMapping("/auth/login") public Api<?> login(@RequestBody Map<String,Object> p){return Api.ok(auth.login(Db.str(p,"username"),Db.str(p,"password")));}
    @PostMapping("/auth/logout") public Api<?> logout(){auth.logout(access.user());return Api.ok(null);}
    @GetMapping("/auth/me") public Api<?> me(){return Api.ok(users.get(access.user().id()));}
    @PutMapping("/me/profile") public Api<?> profile(@RequestBody Map<String,Object> p){return Api.ok(users.updateSelf(access.user().id(),p));}
    @PutMapping("/me/password") public Api<?> password(@RequestBody Map<String,Object> p){auth.changePassword(access.user(),Db.str(p,"old"),Db.str(p,"new"));return Api.ok(null);}
    @PostMapping("/me/photo") public Api<?> photo(@RequestParam MultipartFile file){long id=access.user().id();var saved=media.savePhoto(file);return Api.ok(users.updatePhoto(id,saved.get("url").toString()));}
}
