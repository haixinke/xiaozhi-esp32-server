package xiaozhi.modules.star.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.service.StarService;
import xiaozhi.modules.star.vo.StarBalanceVO;
import xiaozhi.modules.star.vo.StarTransactionVO;

import java.util.List;

/**
 * 星星罐小程序端接口：只读（余额、分页流水）。
 * 所有变动由服务端业务经 StarService 内部触发，不开 HTTP 写接口（防刷星）。
 */
@Tag(name = "星星罐")
@RestController
@RequestMapping("/star")
@RequiredArgsConstructor
public class StarController {

    private final StarService starService;

    @GetMapping("/balance")
    @Operation(summary = "我的星星余额")
    public Result<StarBalanceVO> balance() {
        Long userId = SecurityUser.getUserId();
        return new Result<StarBalanceVO>().ok(StarBalanceVO.of(starService.balance(userId)));
    }

    @GetMapping("/transactions")
    @Operation(summary = "我的星星流水(分页,倒序,赚/花混排)")
    public Result<PageData<StarTransactionVO>> transactions(
            @Parameter(description = "页码,从1开始") @RequestParam(value = "page", defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(value = "limit", defaultValue = "20") long limit) {
        Long userId = SecurityUser.getUserId();
        IPage<StarTransactionEntity> result = starService.transactions(userId, page, limit);
        List<StarTransactionVO> list = result.getRecords().stream()
                .map(StarTransactionVO::toVO)
                .toList();
        return new Result<PageData<StarTransactionVO>>().ok(new PageData<>(list, result.getTotal()));
    }
}
