package xiaozhi.modules.item.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.item.service.ItemService;
import xiaozhi.modules.item.vo.ItemSkuVO;
import xiaozhi.modules.item.vo.UserItemVO;
import xiaozhi.modules.security.user.SecurityUser;

import java.util.List;

@Tag(name = "道具管理")
@RestController
@RequestMapping("/item")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @GetMapping("/skus")
    @Operation(summary = "列出道具SKU（公开）")
    public Result<List<ItemSkuVO>> skus(
            @Parameter(description = "类型：consumable_change/outfit/voice_quota/intimacy")
            @RequestParam(value = "category", required = false) String category) {
        return new Result<List<ItemSkuVO>>().ok(itemService.listSkus(category));
    }

    @GetMapping("/inventory")
    @Operation(summary = "我的道具库存")
    public Result<List<UserItemVO>> inventory() {
        Long userId = SecurityUser.getUserId();
        return new Result<List<UserItemVO>>().ok(itemService.myInventory(userId));
    }
}
