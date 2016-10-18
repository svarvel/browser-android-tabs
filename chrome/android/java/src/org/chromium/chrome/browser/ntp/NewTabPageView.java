// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.graphics.Canvas;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.AdapterDataObserver;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.layouts.content.InvalidationAwareThumbnailProvider;
import org.chromium.chrome.browser.gesturenav.HistoryNavigationLayout;
import org.chromium.chrome.browser.native_page.ContextMenuManager;
import org.chromium.chrome.browser.ntp.NewTabPage.FakeboxDelegate;
import org.chromium.chrome.browser.ntp.cards.NewTabPageAdapter;
import org.chromium.chrome.browser.ntp.cards.NewTabPageRecyclerView;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.suggestions.SuggestionsDependencyFactory;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegate;
import org.chromium.chrome.browser.suggestions.TileGroup;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;
import org.chromium.chrome.browser.widget.displaystyle.ViewResizer;

/**
 * The native new tab page, represented by some basic data such as title and url, and an Android
 * View that displays the page.
 */
public class NewTabPageView extends HistoryNavigationLayout {
    private static final String TAG = "NewTabPageView";

    private NewTabPageRecyclerView mRecyclerView;

    private NewTabPageLayout mNewTabPageLayout;

    private NewTabPageManager mManager;
    private Tab mTab;
    private SnapScrollHelper mSnapScrollHelper;
    private UiConfig mUiConfig;
    private ViewResizer mRecyclerViewResizer;

    private boolean mNewTabPageRecyclerViewChanged;
    private int mSnapshotWidth;
    private int mSnapshotHeight;
    private int mSnapshotScrollY;
    private ContextMenuManager mContextMenuManager;

    /**
     * Manages the view interaction with the rest of the system.
     */
    public interface NewTabPageManager extends SuggestionsUiDelegate {
        /** @return Whether the location bar is shown in the NTP. */
        boolean isLocationBarShownInNTP();

        /** @return Whether voice search is enabled and the microphone should be shown. */
        boolean isVoiceSearchEnabled();

        /**
         * Animates the search box up into the omnibox and bring up the keyboard.
         * @param beginVoiceSearch Whether to begin a voice search.
         * @param pastedText Text to paste in the omnibox after it's been focused. May be null.
         */
        void focusSearchBox(boolean beginVoiceSearch, String pastedText);

        /**
         * @return whether the {@link NewTabPage} associated with this manager is the current page
         * displayed to the user.
         */
        boolean isCurrentPage();

        /**
         * Called when the NTP has completely finished loading (all views will be inflated
         * and any dependent resources will have been loaded).
         */
        void onLoadingComplete();
    }

    /**
     * Default constructor required for XML inflation.
     */
    public NewTabPageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mRecyclerView = new NewTabPageRecyclerView(getContext());

        // Don't attach now, the recyclerView itself will determine when to do it.
        mNewTabPageLayout = (NewTabPageLayout) LayoutInflater.from(getContext())
                                    .inflate(R.layout.new_tab_page_layout, mRecyclerView, false);
    }

    /**
     * Initializes the NTP. This must be called immediately after inflation, before this object is
     * used in any other way.
     *
     * @param manager NewTabPageManager used to perform various actions when the user interacts
     *                with the page.
     * @param tab The Tab that is showing this new tab page.
     * @param searchProviderHasLogo Whether the search provider has a logo.
     * @param searchProviderIsGoogle Whether the search provider is Google.
     * @param scrollPosition The adapter scroll position to initialize to.
     * @param constructedTimeNs The timestamp at which the new tab page's construction started.
     */
    public void initialize(NewTabPageManager manager, Tab tab, TileGroup.Delegate tileGroupDelegate,
            boolean searchProviderHasLogo, boolean searchProviderIsGoogle, int scrollPosition,
            long constructedTimeNs) {
        TraceEvent.begin(TAG + ".initialize()");
        mTab = tab;
        mManager = manager;
        mUiConfig = new UiConfig(this);

        assert manager.getSuggestionsSource() != null;

        // Don't store a direct reference to the activity, because it might change later if the tab
        // is reparented.
        Runnable closeContextMenuCallback = () -> mTab.getActivity().closeContextMenu();
        mContextMenuManager = new ContextMenuManager(mManager.getNavigationDelegate(),
                mRecyclerView::setTouchEnabled, closeContextMenuCallback,
                NewTabPage.CONTEXT_MENU_USER_ACTION_PREFIX);
        mTab.getWindowAndroid().addContextMenuCloseListener(mContextMenuManager);

        mNewTabPageLayout.initialize(manager, tab, tileGroupDelegate, searchProviderHasLogo,
                searchProviderIsGoogle, mRecyclerView, mContextMenuManager, mUiConfig);

        NewTabPageUma.trackTimeToFirstDraw(this, constructedTimeNs);

        mSnapScrollHelper = new SnapScrollHelper(mManager, mNewTabPageLayout);
        mSnapScrollHelper.setView(mRecyclerView);
        mRecyclerView.setSnapScrollHelper(mSnapScrollHelper);
        addView(mRecyclerView);

        mRecyclerView.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean animateMove(ViewHolder holder, int fromX, int fromY, int toX, int toY) {
                // If |mNewTabPageLayout| is animated by the RecyclerView because an item below it
                // was dismissed, avoid also manipulating its vertical offset in our scroll handling
                // at the same time. The onScrolled() method is called when an item is dismissed and
                // the item at the top of the viewport is repositioned.
                if (holder.itemView == mNewTabPageLayout) mNewTabPageLayout.setIsViewMoving(true);

                // Cancel any pending scroll update handling, a new one will be scheduled in
                // onAnimationFinished().
                mSnapScrollHelper.resetSearchBoxOnScroll(false);

                return super.animateMove(holder, fromX, fromY, toX, toY);
            }

            @Override
            public void onAnimationFinished(ViewHolder viewHolder) {
                super.onAnimationFinished(viewHolder);

                // When an item is dismissed, the items at the top of the viewport might not move,
                // and onScrolled() might not be called. We can get in the situation where the
                // toolbar buttons disappear, so schedule an update for it. This can be cancelled
                // from animateMove() in case |mNewTabPageLayout| will be moved. We don't know that
                // from here, as the RecyclerView will animate multiple items when one is dismissed,
                // and some will "finish" synchronously if they are already in the correct place,
                // before other moves have even been scheduled.
                if (viewHolder.itemView == mNewTabPageLayout) {
                    mNewTabPageLayout.setIsViewMoving(false);
                }
                mSnapScrollHelper.resetSearchBoxOnScroll(true);
            }
        });

        Profile profile = Profile.getLastUsedProfile();
        OfflinePageBridge offlinePageBridge =
                SuggestionsDependencyFactory.getInstance().getOfflinePageBridge(profile);
<<<<<<< HEAD
=======
        TileRenderer tileRenderer =
                new TileRenderer(mTab.getActivity(), SuggestionsConfig.getTileStyle(mUiConfig),
                        getTileTitleLines(), mManager.getImageFetcher());
        mTileGroup = new TileGroup(tileRenderer, mManager, mContextMenuManager, tileGroupDelegate,
                /* observer = */ this, offlinePageBridge);

        mSiteSectionViewHolder =
                SiteSection.createViewHolder(mNewTabPageLayout.getSiteSectionView(), mUiConfig);
        mSiteSectionViewHolder.bindDataSource(mTileGroup, tileRenderer);

        /*mSearchProviderLogoView = mNewTabPageLayout.findViewById(R.id.search_provider_logo);
        mLogoDelegate = new LogoDelegateImpl(
                mManager.getNavigationDelegate(), mSearchProviderLogoView, profile);*/

        mSearchBoxView = mNewTabPageLayout.findViewById(R.id.search_box);
        if (SuggestionsConfig.useModernLayout()) {
            mSearchBoxView.setBackgroundResource(R.drawable.ntp_search_box);
            mSearchBoxView.getLayoutParams().height =
                    getResources().getDimensionPixelSize(R.dimen.ntp_search_box_height_modern);
>>>>>>> 1c393f59e6e... Removed Google logo when it is a default Search Engine

        initializeLayoutChangeListener();
        mNewTabPageLayout.setSearchProviderInfo(searchProviderHasLogo, searchProviderIsGoogle);

        mRecyclerView.init(mUiConfig, mContextMenuManager);

        // Set up snippets
        NewTabPageAdapter newTabPageAdapter = new NewTabPageAdapter(
                mManager, mNewTabPageLayout, mUiConfig, offlinePageBridge, mContextMenuManager);
        newTabPageAdapter.refreshSuggestions();
        mRecyclerView.setAdapter(newTabPageAdapter);
        mRecyclerView.getLinearLayoutManager().scrollToPosition(scrollPosition);

        mRecyclerViewResizer = ViewResizer.createAndAttach(mRecyclerView, mUiConfig,
                mRecyclerView.getResources().getDimensionPixelSize(
                        R.dimen.content_suggestions_card_modern_margin),
                mRecyclerView.getResources().getDimensionPixelSize(
                        R.dimen.ntp_wide_card_lateral_margins));

        setupScrollHandling();

        // When the NewTabPageAdapter's data changes we need to invalidate any previous
        // screen captures of the NewTabPageView.
        newTabPageAdapter.registerAdapterDataObserver(new AdapterDataObserver() {
            @Override
            public void onChanged() {
                mNewTabPageRecyclerViewChanged = true;
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                onChanged();
            }
        });

        manager.addDestructionObserver(NewTabPageView.this::onDestroy);

        TraceEvent.end(TAG + ".initialize()");
    }

    /**
     * @return The {@link NewTabPageLayout} displayed in this NewTabPageView.
     */
    NewTabPageLayout getNewTabPageLayout() {
        return mNewTabPageLayout;
    }

    @Override
    public boolean wasLastSideSwipeGestureConsumed() {
        return mRecyclerView.isCardBeingSwiped();
    }

    /**
     * Sets the {@link FakeboxDelegate} associated with the new tab page.
     * @param fakeboxDelegate The {@link FakeboxDelegate} used to determine whether the URL bar
     *                        has focus.
     */
    public void setFakeboxDelegate(FakeboxDelegate fakeboxDelegate) {
        mRecyclerView.setFakeboxDelegate(fakeboxDelegate);
    }

    private void initializeLayoutChangeListener() {
        TraceEvent.begin(TAG + ".initializeLayoutChangeListener()");

        // Listen for layout changes on the NewTabPageView itself to catch changes in scroll
        // position that are due to layout changes after e.g. device rotation. This contrasts with
        // regular scrolling, which is observed through an OnScrollListener.
        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                                          oldBottom) -> { mSnapScrollHelper.handleScroll(); });
        TraceEvent.end(TAG + ".initializeLayoutChangeListener()");
    }

    @VisibleForTesting
    public NewTabPageRecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    /**
     * Adds listeners to scrolling to take care of snap scrolling and updating the search box on
     * scroll.
     */
    private void setupScrollHandling() {
        TraceEvent.begin(TAG + ".setupScrollHandling()");
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                mSnapScrollHelper.handleScroll();
            }
        });

        TraceEvent.end(TAG + ".setupScrollHandling()");
    }

<<<<<<< HEAD
=======
    private void handleScroll() {
        if (mPendingSnapScroll) {
            mRecyclerView.removeCallbacks(mSnapScrollRunnable);
            mRecyclerView.postDelayed(mSnapScrollRunnable, SNAP_SCROLL_DELAY_MS);
        }
        updateSearchBoxOnScroll();
    }

    /**
     * Should be called every time of the flags used to track initialisation progress changes.
     * Finalises initialisation once all the preliminary steps are complete.
     *
     * @see #mHasShownView
     * @see #mTilesLoaded
     */
    private void onInitialisationProgressChanged() {
        if (!hasLoadCompleted()) return;

        mManager.onLoadingComplete();

        // Load the logo after everything else is finished, since it's lower priority.
        loadSearchProviderLogo();
    }

    /**
     * To be called to notify that the tiles have finished loading. Will do nothing if a load was
     * previously completed.
     */
    public void onTilesLoaded() {
        if (mTilesLoaded) return;
        mTilesLoaded = true;

        onInitialisationProgressChanged();
    }

    /**
     * Loads the search provider logo (e.g. Google doodle), if any.
     */
    public void loadSearchProviderLogo() {
        if (!mSearchProviderHasLogo) return;

        mSearchProviderLogoView.showSearchProviderInitialView();

        mLogoDelegate.getSearchProviderLogo(new LogoObserver() {
            @Override
            public void onLogoAvailable(Logo logo, boolean fromCache) {
                if (logo == null && fromCache) return;
                //mSearchProviderLogoView.setDelegate(mLogoDelegate);
                //mSearchProviderLogoView.updateLogo(logo);
                mSnapshotMostVisitedChanged = true;
            }
        });
    }

    /**
     * Changes the layout depending on whether the selected search provider (e.g. Google, Bing)
     * has a logo.
     * @param hasLogo Whether the search provider has a logo.
     * @param isGoogle Whether the search provider is Google.
     */
    public void setSearchProviderInfo(boolean hasLogo, boolean isGoogle) {
        if (hasLogo == mSearchProviderHasLogo && isGoogle == mSearchProviderIsGoogle
                && mInitialized) {
            return;
        }
        mSearchProviderHasLogo = hasLogo;
        mSearchProviderIsGoogle = isGoogle;

        updateTileGridPadding();

        // Hide or show the views above the tile grid as needed, including logo, search box, and
        // spacers.
        int visibility = mSearchProviderHasLogo ? View.VISIBLE : View.GONE;
        int logoVisibility = shouldShowLogo() ? View.VISIBLE : View.GONE;
        int childCount = mNewTabPageLayout.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mNewTabPageLayout.getChildAt(i);
            if (mShortcutsView != null && child == mShortcutsView) break;
            if (child == mSiteSectionViewHolder.itemView) break;

            // Don't change the visibility of a ViewStub as that will automagically inflate it.
            if (child instanceof ViewStub) continue;

            if (child == mSearchProviderLogoView) {
                child.setVisibility(logoVisibility);
            } else {
                child.setVisibility(visibility);
            }
        }

        // Update snap scrolling for the fakebox.
        mRecyclerView.setContainsLocationBar(mManager.isLocationBarShownInNTP());

        updateTileGridPlaceholderVisibility();

        onUrlFocusAnimationChanged();

        updateSearchBoxLogo();

        mSnapshotTileGridChanged = true;
    }

    /**
     * Updates the padding for the tile grid based on what is shown above it.
     */
    private void updateTileGridPadding() {
        int paddingTop;
        if (mShortcutsView != null) {
            // If the shortcuts view is visible, padding will be built into that view.
            paddingTop = 0;
        } else {
            int paddingWithLogoId = SuggestionsConfig.useModernLayout()
                    ? R.dimen.tile_grid_layout_modern_padding_top
                    : R.dimen.tile_grid_layout_padding_top;
            // Set a bit more top padding on the tile grid if there is no logo.
            paddingTop = getResources().getDimensionPixelSize(shouldShowLogo()
                            ? paddingWithLogoId
                            : R.dimen.tile_grid_layout_no_logo_padding_top);
        }

        mSiteSectionViewHolder.itemView.setPadding(
                0, paddingTop, 0, mSiteSectionViewHolder.itemView.getPaddingBottom());
    }

    /**
     * Updates whether the NewTabPage should animate on URL focus changes.
     * @param disable Whether to disable the animations.
     */
    void setUrlFocusAnimationsDisabled(boolean disable) {
        if (disable == mDisableUrlFocusChangeAnimations) return;
        mDisableUrlFocusChangeAnimations = disable;
        if (!disable) onUrlFocusAnimationChanged();
    }

    /**
     * @return Whether URL focus animations are currently disabled.
     */
    boolean urlFocusAnimationsDisabled() {
        return mDisableUrlFocusChangeAnimations;
    }

    /**
     * Specifies the percentage the URL is focused during an animation.  1.0 specifies that the URL
     * bar has focus and has completed the focus animation.  0 is when the URL bar is does not have
     * any focus.
     *
     * @param percent The percentage of the URL bar focus animation.
     */
    void setUrlFocusChangeAnimationPercent(float percent) {
        mUrlFocusChangePercent = percent;
        onUrlFocusAnimationChanged();
    }

    /**
     * @return The percentage that the URL bar is focused during an animation.
     */
    @VisibleForTesting
    float getUrlFocusChangeAnimationPercent() {
        return mUrlFocusChangePercent;
    }

    private void onUrlFocusAnimationChanged() {
        if (mDisableUrlFocusChangeAnimations || FeatureUtilities.isChromeHomeEnabled()
                || mIsMovingNewTabPageView) {
            return;
        }

        // Translate so that the search box is at the top, but only upwards.
        float percent = mSearchProviderHasLogo ? mUrlFocusChangePercent : 0;
        int basePosition = mRecyclerView.computeVerticalScrollOffset()
                + mNewTabPageLayout.getPaddingTop();
        int target = Math.max(basePosition,
                mSearchBoxView.getBottom() - mSearchBoxView.getPaddingBottom()
                        - mSearchBoxBoundsVerticalInset);

        mNewTabPageLayout.setTranslationY(percent * (basePosition - target));
    }

    /**
     * Updates the opacity of the search box when scrolling.
     *
     * @param alpha opacity (alpha) value to use.
     */
    public void setSearchBoxAlpha(float alpha) {
        mSearchBoxView.setAlpha(alpha);

        // Disable the search box contents if it is the process of being animated away.
        ViewUtils.setEnabledRecursive(mSearchBoxView, mSearchBoxView.getAlpha() == 1.0f);
    }

    /**
     * Updates the opacity of the search provider logo when scrolling.
     *
     * @param alpha opacity (alpha) value to use.
     */
    public void setSearchProviderLogoAlpha(float alpha) {
        //mSearchProviderLogoView.setAlpha(alpha);
    }

    /**
     * Set the search box background drawable.
     *
     * @param drawable The search box background.
     */
    public void setSearchBoxBackground(Drawable drawable) {
        mSearchBoxView.setBackground(drawable);
    }

    /**
     * Get the bounds of the search box in relation to the top level NewTabPage view.
     *
     * @param bounds The current drawing location of the search box.
     * @param translation The translation applied to the search box by the parent view hierarchy up
     *                    to the NewTabPage view.
     */
    void getSearchBoxBounds(Rect bounds, Point translation) {
        int searchBoxX = (int) mSearchBoxView.getX();
        int searchBoxY = (int) mSearchBoxView.getY();
        bounds.set(searchBoxX + mSearchBoxView.getPaddingLeft(),
                searchBoxY + mSearchBoxView.getPaddingTop(),
                searchBoxX + mSearchBoxView.getWidth() - mSearchBoxView.getPaddingRight(),
                searchBoxY + mSearchBoxView.getHeight() - mSearchBoxView.getPaddingBottom());

        translation.set(0, 0);

        View view = mSearchBoxView;
        while (true) {
            view = (View) view.getParent();
            if (view == null) {
                // The |mSearchBoxView| is not a child of this view. This can happen if the
                // RecyclerView detaches the NewTabPageLayout after it has been scrolled out of
                // view. Set the translation to the minimum Y value as an approximation.
                translation.y = Integer.MIN_VALUE;
                break;
            }
            translation.offset(-view.getScrollX(), -view.getScrollY());
            if (view == this) break;
            translation.offset((int) view.getX(), (int) view.getY());
        }
        bounds.offset(translation.x, translation.y);

        if (translation.y != Integer.MIN_VALUE) {
            bounds.inset(0, mSearchBoxBoundsVerticalInset);
        }
    }

    /**
     * Sets the listener for search box scroll changes.
     * @param listener The listener to be notified on changes.
     */
    void setSearchBoxScrollListener(OnSearchBoxScrollListener listener) {
        mSearchBoxScrollListener = listener;
        if (mSearchBoxScrollListener != null) updateSearchBoxOnScroll();
    }

>>>>>>> 1c393f59e6e... Removed Google logo when it is a default Search Engine
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Trigger a scroll update when reattaching the window to signal the toolbar that
        // it needs to reset the NTP state. Note that this is handled here rather than
        // NewTabPageLayout#onAttachedToWindow() because NewTabPageLayout may not be
        // immediately attached to the window if the RecyclerView is scrolled when the NTP
        // is refocused.
        if (mManager.isLocationBarShownInNTP()) mNewTabPageLayout.updateSearchBoxOnScroll();
    }

    /**
     * @see InvalidationAwareThumbnailProvider#shouldCaptureThumbnail()
     */
    boolean shouldCaptureThumbnail() {
        if (getWidth() == 0 || getHeight() == 0) return false;

        return mNewTabPageRecyclerViewChanged || mNewTabPageLayout.shouldCaptureThumbnail()
                || getWidth() != mSnapshotWidth || getHeight() != mSnapshotHeight
                || mRecyclerView.computeVerticalScrollOffset() != mSnapshotScrollY;
    }

    /**
     * @see InvalidationAwareThumbnailProvider#captureThumbnail(Canvas)
     */
    void captureThumbnail(Canvas canvas) {
<<<<<<< HEAD
        mNewTabPageLayout.onPreCaptureThumbnail();
=======
        //mSearchProviderLogoView.endFadeAnimation();
>>>>>>> 1c393f59e6e... Removed Google logo when it is a default Search Engine
        ViewUtils.captureBitmap(this, canvas);
        mSnapshotWidth = getWidth();
        mSnapshotHeight = getHeight();
        mSnapshotScrollY = mRecyclerView.computeVerticalScrollOffset();
        mNewTabPageRecyclerViewChanged = false;
    }

    /**
     * @return The adapter position the user has scrolled to.
     */
    public int getScrollPosition() {
        return mRecyclerView.getScrollPosition();
    }

    private void onDestroy() {
        mTab.getWindowAndroid().removeContextMenuCloseListener(mContextMenuManager);
    }

    @VisibleForTesting
    public SnapScrollHelper getSnapScrollHelper() {
        return mSnapScrollHelper;
    }
}
