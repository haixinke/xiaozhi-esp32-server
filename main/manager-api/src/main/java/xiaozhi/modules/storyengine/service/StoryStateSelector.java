package xiaozhi.modules.storyengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xiaozhi.modules.storyengine.model.SelectedStoryState;
import xiaozhi.modules.storyengine.model.StoryActionCandidate;
import xiaozhi.modules.storyengine.model.StoryImageCandidate;
import xiaozhi.modules.storyengine.model.StoryPeriodImageSelection;
import xiaozhi.modules.storyengine.model.StorySceneCandidate;
import xiaozhi.modules.storyengine.model.StorySelectionResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 纯领域故事选择器。负责首次初始化与普通切换的随机选择，不涉及事务与数据库写入。
 * 随机数来源可注入，以支持确定性测试。
 * 特殊图片标签（如卧室窗景）的语义由 SpecialSceneTagRegistry 按场景组合决定，
 * 未命中规则的场景中 tag 不影响主图抽取。
 */
@Component
@RequiredArgsConstructor
public class StoryStateSelector {

    private final StoryRandomSource random;
    private final SpecialSceneTagRegistry tagRegistry;

    /**
     * 首次初始化：在配置完整（有权值且有可用动作）的候选间按相对权重选择，权重合计不要求为 100。
     */
    public StorySelectionResult selectInitial(List<StorySceneCandidate> scenes) {
        // 先做全局配置合法性校验（含负权重/合计超限），非法则整体失败
        if (validatedTotal(scenes, true) < 0) {
            return StorySelectionResult.invalid();
        }
        // 仅保留有权值且有可用动作的候选参与相对权重抽取
        List<StorySceneCandidate> validScenes = scenes.stream()
                .filter(scene -> scene.weight() > 0 && hasEligibleAction(scene))
                .toList();
        int total = validatedTotal(validScenes, false);
        if (total <= 0) {
            return StorySelectionResult.invalid();
        }
        return chooseSceneByRoll(validScenes, random.nextInt(1, total + 1));
    }

    /**
     * 普通切换：按 1~100 真实概率抽取，超出已配置权重合计的部分表示"保持原状态"。
     */
    public StorySelectionResult selectTransition(List<StorySceneCandidate> scenes) {
        int total = validatedTotal(scenes, true);
        if (total < 0) {
            return StorySelectionResult.invalid();
        }
        if (total == 0) {
            return StorySelectionResult.remain();
        }
        int roll = random.nextInt(1, 101);
        // 落入剩余概率，保持原状态
        if (roll > total) {
            return StorySelectionResult.remain();
        }
        return chooseSceneByRoll(scenes, roll);
    }

    /**
     * 图片时段换图：在动作的新时段候选图中等概率选主图，命中特殊标签规则时同步取标签图快照。
     * 原子性：主图缺失，或命中规则但标签图缺失，均返回 empty，调用方保持原状态不变。
     */
    public Optional<StoryPeriodImageSelection> selectPeriodImage(List<StoryImageCandidate> images,
                                                                 String bigSceneName, String smallSceneName) {
        String specialTag = tagRegistry.specialTagOf(bigSceneName, smallSceneName).orElse(null);
        List<StoryImageCandidate> mainImages = images.stream()
                .filter(candidate -> !isSpecialTag(candidate, specialTag))
                .toList();
        if (mainImages.isEmpty()) {
            return Optional.empty();
        }
        String tagImageUrl = null;
        if (specialTag != null) {
            tagImageUrl = images.stream()
                    .filter(candidate -> isSpecialTag(candidate, specialTag))
                    .map(StoryImageCandidate::imageUrl)
                    .findFirst()
                    .orElse(null);
            if (tagImageUrl == null) {
                return Optional.empty();
            }
        }
        StoryImageCandidate image = mainImages.get(random.nextInt(0, mainImages.size()));
        return Optional.of(new StoryPeriodImageSelection(image.id(), image.imageUrl(),
                selectCaption(image.captions()), tagImageUrl));
    }

    /** 按累计权重区间定位命中的小场景 */
    private StorySelectionResult chooseSceneByRoll(List<StorySceneCandidate> scenes, int roll) {
        int cumulative = 0;
        for (StorySceneCandidate scene : scenes) {
            cumulative += scene.weight();
            if (roll <= cumulative) {
                return chooseWithinScene(scene);
            }
        }
        return StorySelectionResult.invalid();
    }

    /** 在命中的小场景内等概率选动作、图片、配文，并在上下限间随机持续小时数 */
    private StorySelectionResult chooseWithinScene(StorySceneCandidate scene) {
        // 特殊标签（如卧室窗景）仅在该场景组合有规则时生效；否则 tag 不影响抽取
        String specialTag = tagRegistry.specialTagOf(scene.bigSceneName(), scene.smallSceneName()).orElse(null);
        List<StoryActionCandidate> eligibleActions = scene.actions().stream()
                .filter(action -> isEligible(action, specialTag))
                .toList();
        // 命中小场景但无可用动作，视为配置失败，不重抽其他场景以免改变概率
        if (eligibleActions.isEmpty()) {
            return StorySelectionResult.invalid();
        }
        StoryActionCandidate action = eligibleActions.get(random.nextInt(0, eligibleActions.size()));
        // 主场景图只在非特殊标签图中等概率选择；特殊标签图仅供快照，不能当主背景
        List<StoryImageCandidate> mainImages = action.images().stream()
                .filter(candidate -> !isSpecialTag(candidate, specialTag))
                .toList();
        StoryImageCandidate image = mainImages.get(random.nextInt(0, mainImages.size()));
        String caption = selectCaption(image.captions());
        // 有规则时取动作内特殊标签图（当前时段候选图首张）URL 快照，供客户端渲染（如窗景）
        String tagImageUrl = specialTag == null ? null : action.images().stream()
                .filter(candidate -> isSpecialTag(candidate, specialTag))
                .map(StoryImageCandidate::imageUrl)
                .findFirst()
                .orElse(null);
        // 持续小时数在 [durationMin, durationMax] 闭区间内等概率选择
        int durationHours = action.durationMin()
                + random.nextInt(0, action.durationMax() - action.durationMin() + 1);
        return StorySelectionResult.selected(new SelectedStoryState(
                scene.bigSceneId(), scene.bigSceneName(), scene.smallSceneId(), scene.smallSceneName(),
                action.id(), action.name(), image.id(), image.imageUrl(), caption, durationHours, tagImageUrl));
    }

    /** 从 | 分隔的配文中去空白后等概率选一条；无非空配文时返回空串 */
    private String selectCaption(String captions) {
        if (captions == null || captions.isBlank()) {
            return "";
        }
        List<String> values = Arrays.stream(captions.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.getFirst();
        }
        return values.get(random.nextInt(0, values.size()));
    }

    private boolean hasEligibleAction(StorySceneCandidate scene) {
        String specialTag = tagRegistry.specialTagOf(scene.bigSceneName(), scene.smallSceneName()).orElse(null);
        return scene.actions().stream().anyMatch(action -> isEligible(action, specialTag));
    }

    /** 动作可用性：时长区间合法（下限>=1 且上限>=下限）且至少有一张可充当主场景图的图片 */
    private boolean isEligible(StoryActionCandidate action, String specialTag) {
        return action.durationMin() >= 1
                && action.durationMax() >= action.durationMin()
                && action.images().stream().anyMatch(candidate -> !isSpecialTag(candidate, specialTag));
    }

    /** 图片是否命中场景特殊标签；无规则（specialTag 为 null）时任何图片都不算特殊 */
    private boolean isSpecialTag(StoryImageCandidate candidate, String specialTag) {
        return specialTag != null && specialTag.equals(candidate.tag());
    }

    /**
     * 校验并汇总权重。返回 -1 表示非法（含 null、负权重；enforceMaximum 时合计超 100 也视为非法）。
     */
    private int validatedTotal(List<StorySceneCandidate> scenes, boolean enforceMaximum) {
        if (scenes == null) {
            return -1;
        }
        long total = 0;
        for (StorySceneCandidate scene : scenes) {
            if (scene == null || scene.weight() < 0) {
                return -1;
            }
            total += scene.weight();
            if (enforceMaximum && total > 100) {
                return -1;
            }
        }
        return total > Integer.MAX_VALUE ? -1 : (int) total;
    }
}
