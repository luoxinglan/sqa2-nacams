package cn.sqa2.nacams.validation;

import cn.sqa2.nacams.auth.Access;
import cn.sqa2.nacams.common.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;

/** 校验预检与照片在线检测（接口规范 §3.7）：全部前端 · 陈彬 BE=唯一权威。 */
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class ValidationController {
    private static final long MAX_PHOTO = 2L * 1024 * 1024;
    private final Access access;
    private final ProfileValidator validator;
    public ValidationController(Access access, ProfileValidator validator) { this.access = access; this.validator = validator; }

    /** POST /validate：复用保存/导入的同一校验器，不合法返 422 且 data.fields 为逐字段错误。 */
    @PostMapping("/validate")
    public Api<?> validate(@RequestBody Map<String, Object> body) {
        access.user();
        String type = Db.str(body, "type");
        if (!Set.of("teacher", "student").contains(type)) throw new ApiException(422, "type仅支持teacher/student");
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = body.get("fields") instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
        fields.put("identityType", type);
        validator.validate(fields);
        return Api.ok(Map.of("valid", true));
    }

    /**
     * POST /photos/check：必检=大小≤2MB、可解码、宽高比 33:48；提示性=背景/着装建议（不阻断）。
     * 返回 {@code {sizeOk, ratioOk, suggestions[]}}。
     */
    @PostMapping("/photos/check")
    public Api<?> checkPhoto(@RequestParam MultipartFile file) {
        access.user();
        var suggestions = new ArrayList<String>();
        boolean sizeOk = file.getSize() <= MAX_PHOTO;
        if (!sizeOk) suggestions.add("照片超过2MB，请压缩后重试");
        boolean ratioOk = false;
        BufferedImage image = null;
        try (InputStream in = file.getInputStream()) { image = ImageIO.read(in); }
        catch (Exception e) { suggestions.add("图片不可解码或格式不受支持"); }
        if (image == null) {
            suggestions.add("图片不可读，请上传可解码的图片");
        } else {
            ratioOk = (long) image.getWidth() * 48 == (long) image.getHeight() * 33;
            if (!ratioOk) suggestions.add("宽高比须为33:48（护照照片48mm×33mm）");
            else suggestions.add(backgroundAdvice(image));
        }
        return Api.ok(Map.of("sizeOk", sizeOk, "ratioOk", ratioOk, "suggestions", suggestions));
    }

    /** 提示性判断（不阻断保存）：以下部中央区平均亮度粗略区分白/非白着装，给出背景色建议。 */
    private String backgroundAdvice(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int x0 = w / 4, x1 = w * 3 / 4, y0 = h / 2, y1 = h;
        long sum = 0; int n = 0;
        int stepX = Math.max(1, w / 50), stepY = Math.max(1, h / 50);
        for (int y = y0; y < y1; y += stepY) for (int x = x0; x < x1; x += stepX) {
            int rgb = image.getRGB(x, y); int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
            sum += (r + g + b) / 3; n++;
        }
        double avg = n == 0 ? 255 : sum / (double) n;
        return avg >= 200 ? "检测到较浅（疑似白色）着装，建议使用淡蓝色背景" : "建议使用白色背景（48mm×33mm 护照照片标准）";
    }
}