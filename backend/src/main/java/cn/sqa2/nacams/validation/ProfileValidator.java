package cn.sqa2.nacams.validation;

import java.util.Map;

/** 陈彬的姓名/电话等校验引擎接入点。所有保存与导入均经过此接口。 */
public interface ProfileValidator {
    void validate(Map<String,Object> profile);
}
