package xiaozhi.modules.storyengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xiaozhi.modules.storyengine.model.SelectedStoryState;
import xiaozhi.modules.storyengine.model.StoryActionCandidate;
import xiaozhi.modules.storyengine.model.StoryImageCandidate;
import xiaozhi.modules.storyengine.model.StorySceneCandidate;
import xiaozhi.modules.storyengine.model.StorySelectionResult;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StoryStateSelector {
    private final StoryRandomSource random;

    public StorySelectionResult selectInitial(List<StorySceneCandidate> scenes) {
        if (validatedTotal(scenes, true) < 0) {
            return StorySelectionResult.invalid();
        }
        List<StorySceneCandidate> validScenes = scenes.stream()
                .filter(scene -> scene.weight() > 0 && hasEligibleAction(scene))
                .toList();
        int total = validatedTotal(validScenes, false);
        if (total <= 0) {
            return StorySelectionResult.invalid();
        }
        return chooseSceneByRoll(validScenes, random.nextInt(1, total + 1));
    }

    public StorySelectionResult selectTransition(List<StorySceneCandidate> scenes) {
        int total = validatedTotal(scenes, true);
        if (total < 0) {
            return StorySelectionResult.invalid();
        }
        if (total == 0) {
            return StorySelectionResult.remain();
        }
        int roll = random.nextInt(1, 101);
        if (roll > total) {
            return StorySelectionResult.remain();
        }
        return chooseSceneByRoll(scenes, roll);
    }

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

    private StorySelectionResult chooseWithinScene(StorySceneCandidate scene) {
        List<StoryActionCandidate> eligibleActions = scene.actions().stream()
                .filter(this::isEligible)
                .toList();
        if (eligibleActions.isEmpty()) {
            return StorySelectionResult.invalid();
        }
        StoryActionCandidate action = eligibleActions.get(random.nextInt(0, eligibleActions.size()));
        StoryImageCandidate image = action.images().get(random.nextInt(0, action.images().size()));
        String caption = selectCaption(image.captions());
        int durationHours = action.durationMin()
                + random.nextInt(0, action.durationMax() - action.durationMin() + 1);
        return StorySelectionResult.selected(new SelectedStoryState(
                scene.bigSceneId(), scene.bigSceneName(), scene.smallSceneId(), scene.smallSceneName(),
                action.id(), action.name(), image.id(), image.imageUrl(), caption, durationHours));
    }

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
        return scene.actions().stream().anyMatch(this::isEligible);
    }

    private boolean isEligible(StoryActionCandidate action) {
        return action.durationMin() >= 1
                && action.durationMax() >= action.durationMin()
                && !action.images().isEmpty();
    }

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
