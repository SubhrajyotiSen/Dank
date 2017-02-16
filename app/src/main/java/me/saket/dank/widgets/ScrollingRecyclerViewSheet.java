package me.saket.dank.widgets;

import android.content.Context;
import android.os.Handler;
import android.support.v4.view.NestedScrollingParent;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Scroller;

/**
 * A scrollable sheet that can wrap a RecyclerView and scroll together (not in parallel) in a nested manner.
 * This sheet consumes all scrolls made on a RecyclerView if it can scroll/fling any further in the direction
 * of the scroll.
 */
public class ScrollingRecyclerViewSheet extends FrameLayout implements NestedScrollingParent {

    private final Scroller flingScroller;
    private final int minimumFlingVelocity;
    private final int maximumFlingVelocity;

    public ScrollingRecyclerViewSheet(Context context, AttributeSet attrs) {
        super(context, attrs);
        flingScroller = new Scroller(context);
        minimumFlingVelocity = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
        maximumFlingVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() != 0) {
            throw new AssertionError("Can only host one RecyclerView");
        }
        if (!(child instanceof RecyclerView)) {
            throw new AssertionError("Only RecyclerView is supported");
        }
        super.addView(child, index, params);

        RecyclerView recyclerView = (RecyclerView) child;
        recyclerView.addOnScrollListener(scrollListener);
        recyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
    }

    private boolean isSheetFullyExpanded() {
        return getCurrentTopY() <= 0;
    }

    private boolean isSheetFullyHidden() {
        return getCurrentTopY() >= getHeight();
    }

    private float getCurrentTopY() {
        return getTranslationY();
    }

    private void adjustOffsetBy(float dy) {
        setTranslationY(getTranslationY() - dy);
    }

    private float attemptToConsumeScrollY(View target, float dy) {
        boolean scrollingDownwards = dy > 0;
        if (scrollingDownwards) {
            if (!isSheetFullyExpanded()) {
                float adjustedDy = dy;
                if (getCurrentTopY() - dy < 0) {
                    // Don't let the sheet go beyond its top bounds.
                    adjustedDy = getCurrentTopY();
                }

                adjustOffsetBy(adjustedDy);
                return adjustedDy;
            }

        } else {
            boolean canChildViewScrollDownwardsAnymore = target.canScrollVertically(-1);
            if (!isSheetFullyHidden() && !canChildViewScrollDownwardsAnymore) {
                float adjustedDy = dy;
                if (getCurrentTopY() - dy > getHeight()) {
                    // Don't let the sheet go beyond its bottom bounds.
                    adjustedDy = getCurrentTopY() - getHeight();
                }

                adjustOffsetBy(adjustedDy);
                return adjustedDy;
            }
        }

        return 0;
    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        // Always accept nested scroll events from the child. The decision of whether
        // or not to actually scroll is calculated inside onNestedPreScroll().
        return true;
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        flingScroller.forceFinished(true);
        float consumedY = attemptToConsumeScrollY(target, dy);
        consumed[1] = (int) consumedY;
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        flingScroller.forceFinished(true);

        float velocityYAbs = Math.abs(velocityY);
        if (velocityYAbs > minimumFlingVelocity && velocityYAbs < maximumFlingVelocity) {
            // Start flinging!
            flingScroller.fling(0, 0, (int) velocityX, (int) velocityY, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);

            new Handler().post(new Runnable() {
                private float lastY = flingScroller.getStartY();

                @Override
                public void run() {
                    boolean isFlingOngoing = flingScroller.computeScrollOffset();
                    if (isFlingOngoing) {
                        float dY = flingScroller.getCurrY() - lastY;
                        lastY = flingScroller.getCurrY();
                        float distanceConsumed = attemptToConsumeScrollY(target, dY);

                        // As soon as we stop scrolling, transfer the fling to the recyclerView.
                        // This is hacky, but it works.
                        if (distanceConsumed == 0f) {
                            float transferVelocity = flingScroller.getCurrVelocity();
                            if (velocityY < 0) {
                                transferVelocity *= -1;
                            }
                            RecyclerView recyclerView = (RecyclerView) target;
                            recyclerView.fling(0, ((int) transferVelocity));

                        } else {
                            // There's still more distance to be covered in this fling. Keep scrolling!
                            post(this);
                        }
                    }
                }
            });

            // Consume all flings on the recyclerView. We'll manually check if they can actually be
            // used to scroll this sheet any further in the fling direction. If not, the fling is
            // transferred back to the RecyclerView.
            return true;

        } else {
            return super.onNestedPreFling(target, velocityX, velocityY);
        }
    }

    private RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (!flingScroller.isFinished()) {
                // A fling is ongoing in the RecyclerView. Listen for the scroll offset and transfer
                // the fling to NonSnappingBottomSheet as soon as the recyclerView reaches the top.
                boolean hasReachedTop = recyclerView.computeVerticalScrollOffset() == 0;
                if (hasReachedTop) {
                    // For some reasons, the sheet starts scrolling at a much higher velocity when the
                    // fling is transferred.
                    float transferVelocity = flingScroller.getCurrVelocity() / 4;
                    if (dy < 0) {
                        transferVelocity *= -1;
                    }
                    onNestedPreFling(recyclerView, 0, transferVelocity);
                }
            }
        }
    };

}
