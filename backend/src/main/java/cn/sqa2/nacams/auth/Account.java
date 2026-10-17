package cn.sqa2.nacams.auth;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

@TableName("users")
public class Account {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String username;
    public String passwordHash;
    public String role;
    public String identityType;
    public String name;
    public Boolean isDirector;
    public Boolean isLibrarian;
    public Boolean active;
    public String profileJson;
}
