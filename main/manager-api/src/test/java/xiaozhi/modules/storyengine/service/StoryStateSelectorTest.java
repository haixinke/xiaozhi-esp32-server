package xiaozhi.modules.storyengine.service;

import org.junit.jupiter.api.Test;
import xiaozhi.modules.storyengine.model.StoryActionCandidate;
import xiaozhi.modules.storyengine.model.StoryImageCandidate;
import xiaozhi.modules.storyengine.model.StorySceneCandidate;
import xiaozhi.modules.storyengine.model.StorySelectionResult;
import xiaozhi.modules.storyengine.model.StorySelectionResultType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryStateSelectorTest {

    @Test
    void transitionKeepsStateWhenRollFallsIntoRemainingProbability() {
        StoryStateSelector selector = selectorWithRolls(draw(1, 101, 81));
        StorySceneCandidate scene = scene("卧室", 80, validAction(1, 2));

        assertThat(selector.selectTransition(List.of(scene)).type())
                .isEqualTo(StorySelectionResultType.REMAIN);
    }

    @Test
    void transitionSelectsAdjacentScenesAtAndAfterCumulativeBoundary() {
        QueueRandomSource random = randomWithRolls(
                draw(1, 101, 40), draw(0, 1, 0), draw(0, 1, 0), draw(0, 1, 0),
                draw(1, 101, 41), draw(0, 1, 0), draw(0, 1, 0), draw(0, 1, 0));
        StoryStateSelector selector = new StoryStateSelector(random, new SpecialSceneTagRegistry());
        List<StorySceneCandidate> scenes = List.of(
                scene("边界前", 40, validAction(1, 1)),
                scene("边界后", 60, validAction(1, 1)));

        assertThat(selector.selectTransition(scenes).state().smallSceneName()).isEqualTo("边界前");
        assertThat(selector.selectTransition(scenes).state().smallSceneName()).isEqualTo("边界后");
        random.assertAllDrawsConsumed();
    }

    @Test
    void initialSelectionNormalizesValidWeightsAndSelectsCompleteSnapshot() {
        QueueRandomSource random = randomWithRolls(
                draw(1, 71, 60), draw(0, 1, 0), draw(0, 1, 0), draw(0, 2, 1));
        StoryStateSelector selector = new StoryStateSelector(random, new SpecialSceneTagRegistry());

        StorySelectionResult result = selector.selectInitial(List.of(
                scene("卧室", 40, validAction(1, 2)),
                scene("公园", 30, validAction(1, 2)),
                scene("无图场景", 30)));

        assertThat(result.type()).isEqualTo(StorySelectionResultType.SELECTED);
        assertThat(result.state().smallSceneName()).isEqualTo("公园");
        assertThat(result.state().durationHours()).isEqualTo(2);
        random.assertAllDrawsConsumed();
    }

    @Test
    void transitionRejectsInvalidGlobalWeightInsteadOfTruncatingIt() {
        StoryStateSelector selector = selectorWithRolls();

        assertThat(selector.selectTransition(List.of(scene("A", 60), scene("B", 50))).type())
                .isEqualTo(StorySelectionResultType.INVALID_CONFIGURATION);
    }

    @Test
    void transitionKeepsStateWhenAllWeightsAreZero() {
        StoryStateSelector selector = selectorWithRolls();

        assertThat(selector.selectTransition(List.of(scene("A", 0), scene("B", 0))).type())
                .isEqualTo(StorySelectionResultType.REMAIN);
    }

    @Test
    void selectionRejectsNegativeWeightWithoutDrawingRandomness() {
        StoryStateSelector selector = selectorWithRolls();

        assertThat(selector.selectInitial(List.of(scene("A", -1, validAction(1, 1)))).type())
                .isEqualTo(StorySelectionResultType.INVALID_CONFIGURATION);
    }

    @Test
    void selectionRejectsSelectedSceneWithoutEligibleAction() {
        StoryStateSelector selector = selectorWithRolls(draw(1, 101, 1));

        assertThat(selector.selectTransition(List.of(scene("A", 100))).type())
                .isEqualTo(StorySelectionResultType.INVALID_CONFIGURATION);
    }

    @Test
    void selectionRejectsInvalidDurationBounds() {
        StoryStateSelector selector = selectorWithRolls(draw(1, 101, 1));
        StoryActionCandidate invalidDuration = action("invalid", 0, 1, image("image", "", "url"));

        assertThat(selector.selectTransition(List.of(scene("A", 100, invalidDuration))).type())
                .isEqualTo(StorySelectionResultType.INVALID_CONFIGURATION);
    }

    @Test
    void selectionSamplesBothInclusiveDurationEndpoints() {
        QueueRandomSource random = randomWithRolls(
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 1, 0), draw(0, 2, 0),
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 1, 0), draw(0, 2, 1));
        StoryStateSelector selector = new StoryStateSelector(random, new SpecialSceneTagRegistry());
        StorySceneCandidate scene = scene("卧室", 100, validAction(1, 2));

        assertThat(selector.selectTransition(List.of(scene)).state().durationHours()).isEqualTo(1);
        assertThat(selector.selectTransition(List.of(scene)).state().durationHours()).isEqualTo(2);
        random.assertAllDrawsConsumed();
    }

    @Test
    void selectionExcludesInvalidActionsBeforeDrawingAnEligibleAction() {
        QueueRandomSource random = randomWithRolls(
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 1, 0), draw(0, 1, 0));
        StoryStateSelector selector = new StoryStateSelector(random, new SpecialSceneTagRegistry());
        StoryActionCandidate invalid = action("invalid", 0, 1, image("invalid-image", "", "invalid-url"));
        StoryActionCandidate valid = action("valid", 1, 1, image("valid-image", "", "valid-url"));

        StorySelectionResult result = selector.selectTransition(List.of(scene("卧室", 100, invalid, valid)));

        assertThat(result.state().actionId()).isEqualTo("valid-id");
        random.assertAllDrawsConsumed();
    }

    @Test
    void selectionSelectsImageAndNormalizesDelimitedCaption() {
        StoryStateSelector selector = selectorWithRolls(
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 2, 1), draw(0, 1, 0));
        StoryActionCandidate action = action("散步", 1, 1,
                image("first", "  早安  | |  晚安  ", "first-url"),
                image("second", "ignored", "second-url"));

        StorySelectionResult result = selector.selectTransition(List.of(scene("公园", 100, action)));

        assertThat(result.state().actionImageId()).isEqualTo("second");
        assertThat(result.state().imageUrl()).isEqualTo("second-url");
        assertThat(result.state().caption()).isEqualTo("ignored");
        assertThat(result.state().durationHours()).isEqualTo(1);
    }

    @Test
    void selectionUsesTrimmedCaptionChoiceAndEmptyCaptionWhenNoneIsPresent() {
        StoryStateSelector withCaption = selectorWithRolls(
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 1, 0), draw(0, 2, 1), draw(0, 1, 0));
        StoryActionCandidate captioned = action("散步", 1, 1,
                image("image", "  早安 |  | 晚安  ", "url"));

        assertThat(withCaption.selectTransition(List.of(scene("公园", 100, captioned))).state().caption())
                .isEqualTo("晚安");

        StoryStateSelector emptyCaption = selectorWithRolls(
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 1, 0), draw(0, 1, 0));
        StoryActionCandidate blank = action("休息", 1, 1, image("image", " |   | ", "url"));

        assertThat(emptyCaption.selectTransition(List.of(scene("卧室", 100, blank))).state().caption())
                .isEmpty();
    }

    @Test
    void transitionCanSelectTheSameIdsRepeatedly() {
        StoryStateSelector selector = selectorWithRolls(
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 1, 0), draw(0, 1, 0),
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 1, 0), draw(0, 1, 0));
        StorySceneCandidate scene = scene("卧室", 100, validAction(1, 1));

        StorySelectionResult first = selector.selectTransition(List.of(scene));
        StorySelectionResult second = selector.selectTransition(List.of(scene));

        assertThat(second.state()).isEqualTo(first.state());
    }

    @Test
    void initialSelectionRejectsEmptyCandidatesAndTransitionRemainsForNullInput() {
        StoryStateSelector selector = selectorWithRolls();

        assertThat(selector.selectInitial(List.of()).type()).isEqualTo(StorySelectionResultType.INVALID_CONFIGURATION);
        assertThat(selector.selectTransition(null).type()).isEqualTo(StorySelectionResultType.INVALID_CONFIGURATION);
    }

    @Test
    void candidateListsAreDefensiveCopies() {
        List<StoryImageCandidate> images = new ArrayList<>(List.of(image("image", "", "url")));
        StoryActionCandidate action = new StoryActionCandidate("action", "休息", 1, 1, images);
        images.clear();

        assertThat(action.images()).hasSize(1);
        assertThatThrownBy(() -> action.images().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void selectedStateCarriesWindowTagImageUrlWhenActionHasWindowImage() {
        // 在家+卧室场景下，动作含 tag=窗户 图：tagImageUrl 取该图 URL，主图只能是非窗户图
        StoryImageCandidate windowImage = taggedImage("window-id", "窗户", "https://example.com/window.png");
        StoryImageCandidate normalImage = image("image-id", "", "https://example.com/image.png");
        StoryActionCandidate action = action("动作", 1, 1, normalImage, windowImage);
        StoryStateSelector selector = selectorWithRolls(
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 1, 0), draw(0, 1, 0));

        StorySelectionResult result = selector.selectInitial(List.of(bedroomScene(100, action)));

        assertThat(result.type()).isEqualTo(StorySelectionResultType.SELECTED);
        assertThat(result.state().imageUrl()).isEqualTo("https://example.com/image.png");
        assertThat(result.state().tagImageUrl()).isEqualTo("https://example.com/window.png");
    }

    @Test
    void mainImageNeverFallsBackToWindowTaggedImage() {
        // 主图抽取范围不含窗户图：即使随机数指向窗户图原下标，主图也只能命中非窗户图
        StoryImageCandidate windowImage = taggedImage("window-id", "窗户", "https://example.com/window.png");
        StoryImageCandidate firstImage = image("first-id", "", "https://example.com/first.png");
        StoryImageCandidate secondImage = image("second-id", "", "https://example.com/second.png");
        StoryActionCandidate action = action("动作", 1, 1, firstImage, windowImage, secondImage);
        StoryStateSelector selector = selectorWithRolls(
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 2, 1), draw(0, 1, 0));

        StorySelectionResult result = selector.selectInitial(List.of(bedroomScene(100, action)));

        assertThat(result.type()).isEqualTo(StorySelectionResultType.SELECTED);
        assertThat(result.state().imageUrl()).isEqualTo("https://example.com/second.png");
        assertThat(result.state().tagImageUrl()).isEqualTo("https://example.com/window.png");
    }

    @Test
    void actionWithOnlyWindowTaggedImagesIsIneligible() {
        // 卧室场景动作只有窗户图时没有主图可用，视为配置失败而不是把窗景当主场景
        StoryActionCandidate action = action("动作", 1, 1,
                taggedImage("window-id", "窗户", "https://example.com/window.png"));

        StorySelectionResult result = selectorWithRolls().selectInitial(List.of(bedroomScene(100, action)));

        assertThat(result.type()).isEqualTo(StorySelectionResultType.INVALID_CONFIGURATION);
    }

    @Test
    void selectedStateHasNullTagImageUrlWhenActionHasNoWindowImage() {
        StoryActionCandidate action = validAction(1, 1);
        StoryStateSelector selector = selectorWithRolls(
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 1, 0), draw(0, 1, 0));

        StorySelectionResult result = selector.selectInitial(List.of(bedroomScene(100, action)));

        assertThat(result.type()).isEqualTo(StorySelectionResultType.SELECTED);
        assertThat(result.state().tagImageUrl()).isNull();
    }

    @Test
    void windowTaggedImageIsOrdinaryMainImageOutsideBedroomRule() {
        // 未命中特殊标签规则的场景：tag=窗户 仅为运营备注，图片正常参与主图抽取且 tagImageUrl 为 null
        StoryImageCandidate windowImage = taggedImage("window-id", "窗户", "https://example.com/window.png");
        StoryImageCandidate normalImage = image("image-id", "", "https://example.com/image.png");
        StoryActionCandidate action = action("动作", 1, 1, normalImage, windowImage);
        StoryStateSelector selector = selectorWithRolls(
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 2, 1), draw(0, 1, 0));

        StorySelectionResult result = selector.selectInitial(List.of(scene("公园", 100, action)));

        assertThat(result.type()).isEqualTo(StorySelectionResultType.SELECTED);
        assertThat(result.state().imageUrl()).isEqualTo("https://example.com/window.png");
        assertThat(result.state().tagImageUrl()).isNull();
    }

    @Test
    void windowTaggedImageIsOrdinaryWhenBigSceneDoesNotMatchRule() {
        // 小场景名为卧室但大场景不是"在家"，规则不生效
        StoryActionCandidate action = action("动作", 1, 1,
                taggedImage("window-id", "窗户", "https://example.com/window.png"));
        StoryStateSelector selector = selectorWithRolls(
                draw(1, 101, 1), draw(0, 1, 0), draw(0, 1, 0), draw(0, 1, 0));

        StorySelectionResult result = selector.selectInitial(List.of(scene("卧室", 100, action)));

        assertThat(result.type()).isEqualTo(StorySelectionResultType.SELECTED);
        assertThat(result.state().imageUrl()).isEqualTo("https://example.com/window.png");
        assertThat(result.state().tagImageUrl()).isNull();
    }

    private StoryStateSelector selectorWithRolls(ExpectedDraw... draws) {
        return new StoryStateSelector(randomWithRolls(draws), new SpecialSceneTagRegistry());
    }

    private QueueRandomSource randomWithRolls(ExpectedDraw... draws) {
        return new QueueRandomSource(draws);
    }

    private static ExpectedDraw draw(int originInclusive, int boundExclusive, int value) {
        return new ExpectedDraw(originInclusive, boundExclusive, value);
    }

    private static StorySceneCandidate scene(String name, int weight, StoryActionCandidate... actions) {
        return new StorySceneCandidate("big-id", "大场景", name + "-id", name, weight, List.of(actions));
    }

    // 命中窗户特殊标签规则的场景组合：大场景=在家 + 小场景=卧室
    private static StorySceneCandidate bedroomScene(int weight, StoryActionCandidate... actions) {
        return new StorySceneCandidate("home-id", "在家", "bedroom-id", "卧室", weight, List.of(actions));
    }

    private static StoryActionCandidate validAction(int durationMin, int durationMax) {
        return action("动作", durationMin, durationMax, image("image-id", "", "image-url"));
    }

    private static StoryActionCandidate action(String name, int durationMin, int durationMax,
                                                StoryImageCandidate... images) {
        return new StoryActionCandidate(name + "-id", name, durationMin, durationMax, List.of(images));
    }

    private static StoryImageCandidate image(String id, String captions, String imageUrl) {
        return new StoryImageCandidate(id, imageUrl, captions, null);
    }

    private static StoryImageCandidate taggedImage(String id, String tag, String imageUrl) {
        return new StoryImageCandidate(id, imageUrl, "", tag);
    }

    private record ExpectedDraw(int originInclusive, int boundExclusive, int value) {
    }

    private static final class QueueRandomSource implements StoryRandomSource {
        private final Queue<ExpectedDraw> expectedDraws;

        private QueueRandomSource(ExpectedDraw... draws) {
            expectedDraws = new ArrayDeque<>(List.of(draws));
        }

        @Override
        public int nextInt(int originInclusive, int boundExclusive) {
            ExpectedDraw expected = expectedDraws.remove();
            assertThat(originInclusive).isEqualTo(expected.originInclusive());
            assertThat(boundExclusive).isEqualTo(expected.boundExclusive());
            return expected.value();
        }

        private void assertAllDrawsConsumed() {
            assertThat(expectedDraws).isEmpty();
        }
    }
}
