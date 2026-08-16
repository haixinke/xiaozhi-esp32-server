package xiaozhi.modules.storyengine.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 场景特殊图片标签注册表：声明"哪个场景组合下哪个 tag 有特殊语义"。
 * <p>
 * 特殊语义 = 命中规则的场景中，该 tag 的图片不参与主图等概率抽取，其 URL 快照写入
 * 状态的 tagImageUrl 字段（如卧室的窗景图）。未命中规则的场景中 tag 仅为运营备注，
 * 所有图片一视同仁参与主图抽取。
 * <p>
 * 扩展方式：新增场景特殊标签时在 RULES 追加一行 Rule，选择器零改动。
 * 规则按场景名称匹配——名称即业务标识；运营在智控台改名会使规则失效，属低频操作。
 */
@Component
public class SpecialSceneTagRegistry {

    /** 特殊标签规则：bigSceneName + smallSceneName 同时命中时，tag 具有特殊语义 */
    private record Rule(String bigSceneName, String smallSceneName, String tag) {
    }

    /** 规则表：新场景特殊标签在此追加 */
    private static final List<Rule> RULES = List.of(
            new Rule("在家", "卧室", "窗户")
    );

    /**
     * 查询场景组合对应的特殊标签；无规则时返回 empty。
     */
    public Optional<String> specialTagOf(String bigSceneName, String smallSceneName) {
        if (bigSceneName == null || smallSceneName == null) {
            return Optional.empty();
        }
        return RULES.stream()
                .filter(rule -> rule.bigSceneName().equals(bigSceneName)
                        && rule.smallSceneName().equals(smallSceneName))
                .map(Rule::tag)
                .findFirst();
    }
}
