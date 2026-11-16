/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import proxy.share.dc.LocaleHelper;
import proxy.share.dc.R;

public abstract class BaseActivity extends AppCompatActivity {
    private String signature;
    private FrameLayout frame;
    private FrameLayout contentHolder;
    private LiveBackgroundView live;
    private NavCapsule nav;
    private int bottomInset;
    private final Runnable resumeBackground = new Runnable() {
        @Override
        public void run() {
            if (live != null) live.setScrollPaused(false);
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        signature = ThemeManager.signature(this);
    }

    @Override
    public void setContentView(int layoutResID) {
        frame = new FrameLayout(this);
        live = new LiveBackgroundView(this);
        frame.addView(live, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        contentHolder = new FrameLayout(this);
        int maxWidth = getResources().getDimensionPixelSize(R.dimen.content_max_width);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                maxWidth > 0 ? maxWidth : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        frame.addView(contentHolder, clp);
        LayoutInflater.from(this).inflate(layoutResID, contentHolder, true);
        super.setContentView(frame);
        ThemeManager.applyWindow(this);
        ViewCompat.setOnApplyWindowInsetsListener(frame, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            bottomInset = bars.bottom;
            contentHolder.setPadding(bars.left, bars.top, bars.right, 0);
            if (nav != null) nav.applyBottomInset(bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(frame);
    }

    protected NavCapsule installNav(int selectedIndex, NavCapsule.OnSelectListener listener) {
        String[] icons = {"ic_home", "ic_activity", "ic_settings"};
        int[] labels = {R.string.nav_home, R.string.nav_monitor, R.string.nav_settings};
        nav = new NavCapsule(this, icons, labels, selectedIndex, -1);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, NavCapsule.heightDp(this)));
        int margin = getResources().getDimensionPixelSize(R.dimen.nav_margin_h);
        lp.gravity = Gravity.BOTTOM;
        lp.setMarginStart(margin);
        lp.setMarginEnd(margin);
        lp.bottomMargin = UiKit.dp(this, 12) + bottomInset;
        frame.addView(nav, lp);
        nav.setOnSelectListener(listener);
        return nav;
    }

    public void setNavClearance(View scrollContent) {
        int base = UiKit.dp(this, NavCapsule.heightDp(this) + 12 + 28);
        scrollContent.setPadding(scrollContent.getPaddingLeft(), scrollContent.getPaddingTop(),
                scrollContent.getPaddingRight(), base);
    }

    public void bindScrollPause(View scrollable) {
        scrollable.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (live == null) return;
            live.setScrollPaused(true);
            live.removeCallbacks(resumeBackground);
            live.postDelayed(resumeBackground, 220);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (signature != null && !signature.equals(ThemeManager.signature(this))) {
            recreate();
        }
    }
}
