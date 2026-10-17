package cn.sqa2.nacams.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class Errors {
    private static final Logger LOG = LoggerFactory.getLogger(Errors.class);
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Api<Object>> business(ApiException e) {
        return ResponseEntity.status(e.code).body(new Api<>(e.code, e.getMessage(), e.data));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Api<Object>> conflict(Exception e) {
        return business(new ApiException(409, "编号或用户名重复，或记录仍被业务引用"));
    }
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Api<Object>> badRequest(Exception e) {
        return business(new ApiException(400, "请求参数或格式错误"));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Api<Object>> tooLarge(Exception e) { return business(new ApiException(413, "上传文件超出限制")); }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Api<Object>> notFound(Exception e) { return business(new ApiException(404,"接口或资源不存在")); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Api<Object>> wrongMethod(Exception e) { return business(new ApiException(405,"HTTP方法不支持")); }
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<Api<Object>> missingFile(Exception e) { return business(new ApiException(400,"缺少上传文件file")); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Api<Object>> unexpected(Exception e) {
        LOG.error("Request failed", e);
        return business(new ApiException(500, "服务暂时不可用，请联系管理员"));
    }
}
