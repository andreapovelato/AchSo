package fi.aalto.legroup.achso.annotation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import fi.aalto.legroup.achso.annotation.renderers.AnnotationRenderer;
import fi.aalto.legroup.achso.entities.Annotation;

/**
 * Renders annotations using a pipeline of renderers.
 *
 * @author Leo Nikkilä
 */
public class AnnotationRenderService {

    // How far off annotations can be before rendering them with fuzzy rendering (in milliseconds)
    private static final int RENDERING_FUZZINESS = 500;

    // Use LinkedHashSet since renderer order may be important
    protected Set<AnnotationRenderer> pipeline = new LinkedHashSet<>();

    protected List<Annotation> annotations = new ArrayList<>();
    protected List<Annotation> renderQueue = new ArrayList<>();

    // Keep track of rendered annotations using a list. We can't store and compare the playback
    // position, since it's unreliable with MediaPlayer. This is a bit more memory-intensive but
    // will not result in rendering the same annotations again.
    protected List<Annotation> renderedAnnotations = new ArrayList<>();

    public void addRenderer(AnnotationRenderer renderer) {
        renderer.initialize(this);
        pipeline.add(renderer);
    }

    public void removeRenderer(AnnotationRenderer renderer) {
        pipeline.remove(renderer);
    }

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }

    /**
     * Recalculate rendered annotations to reflect a new playback position.
     *
     * @param position new playback position in milliseconds
     */
    public void recalculateRendered(long position) {
        renderedAnnotations.clear();

        for (Annotation annotation : annotations) {
            if (annotation.getTime() < position) renderedAnnotations.add(annotation);
        }
    }

    /**
     * Render annotations at a certain position.
     *
     * @param position current playback position in milliseconds
     */
    public void render(long position) {
        for (Annotation annotation : annotations) {
            if (annotation.getTime() > position) continue;
            if (renderedAnnotations.contains(annotation)) continue;

            renderQueue.add(annotation);
            renderedAnnotations.add(annotation);
        }

        clear();
        postRenderQueue();
    }

    /**
     * Render annotations within a certain frame of inaccuracy. If multiple annotations set to
     * different time points are within that frame, the closest time point will be rendered. Useful
     * for e.g. seeking where it's difficult to pinpoint the exact position.
     *
     * @param position current playback position in milliseconds
     */
    public void fuzzyRender(long position) {
        long closestPosition = -1;
        long smallestDifference = RENDERING_FUZZINESS;

        for (Annotation annotation : annotations) {
            long time = annotation.getTime();
            long difference = Math.abs(position - time);

            if (difference > RENDERING_FUZZINESS) continue;
            if (difference > smallestDifference) continue;

            closestPosition = time;
            smallestDifference = difference;
        }

        clear();

        // Abort if no annotations were found
        if (closestPosition == -1) return;

        for (Annotation annotation : annotations) {
            long time = annotation.getTime();
            if (time == closestPosition) renderQueue.add(annotation);
        }

        postRenderQueue();
    }

    protected void postRenderQueue() {
        if (renderQueue.isEmpty()) return;

        for (AnnotationRenderer renderer : pipeline) {
            renderer.render(renderQueue);
        }

        renderQueue.clear();
    }

    public void clear() {
        for (AnnotationRenderer renderer : pipeline) {
            renderer.clear();
        }
    }

    public void release() {
        for (AnnotationRenderer renderer : pipeline) {
            renderer.release();
        }

        // Don't hold onto annotations or renderers
        annotations.clear();
        pipeline.clear();
    }

}
