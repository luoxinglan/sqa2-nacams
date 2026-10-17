package cn.sqa2.nacams.admin;

import cn.sqa2.nacams.auth.Access;
import cn.sqa2.nacams.common.Api;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserController {
    private final Access access;private final UserService users;
    public UserController(Access access,UserService users){this.access=access;this.users=users;}
    @GetMapping public Api<?> list(@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String role,
            @RequestParam(defaultValue="1")int page,@RequestParam(name="page_size",defaultValue="20")int size){access.admin();return Api.ok(users.list(keyword,role,page,size));}
    @GetMapping("/{id}") public Api<?> get(@PathVariable long id){access.admin();return Api.ok(users.get(id));}
    @PostMapping public Api<?> create(@RequestBody Map<String,Object> p){access.admin();return Api.ok(users.save(null,p));}
    @PutMapping("/{id}") public Api<?> update(@PathVariable long id,@RequestBody Map<String,Object> p){access.admin();return Api.ok(users.save(id,p));}
    @DeleteMapping("/{id}") public Api<?> delete(@PathVariable long id){access.admin();users.deactivate(id);return Api.ok(null);}
}
