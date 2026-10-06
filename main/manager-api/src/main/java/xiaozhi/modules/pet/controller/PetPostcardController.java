package xiaozhi.modules.pet.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pet.service.PetPostcardService;
import xiaozhi.modules.pet.vo.PetPostcardPublicVO;

/**
 * 宠物明信片控制器。
 *
 * <p>公开分享接口：匿名可访问（Shiro filterMap 配置 anon），
 * 好友点开分享卡片即可免授权查看，不要求登录/手机号绑定。
 */
@Tag(name = "宠物明信片")
@RestController
@RequestMapping("/pet/postcard")
@AllArgsConstructor
public class PetPostcardController {

    private final PetPostcardService petPostcardService;

    @GetMapping("/public/{shareId}")
    @Operation(summary = "明信片公开查看（免授权，好友分享入口）")
    public Result<PetPostcardPublicVO> getPublic(@PathVariable("shareId") String shareId) {
        return new Result<PetPostcardPublicVO>().ok(petPostcardService.getPublicByShareId(shareId));
    }
}
