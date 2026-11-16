/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import android.os.Bundle;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import proxy.share.dc.ui.AppPreferences;
import proxy.share.dc.ui.BaseActivity;
import proxy.share.dc.ui.NavCapsule;

public class MainActivity extends BaseActivity {
    public static final int PAGE_HOME = 0;
    public static final int PAGE_MONITOR = 1;
    public static final int PAGE_SETTINGS = 2;
    private static final String[] TAGS = {"page_home", "page_monitor", "page_settings"};
    private static final String STATE_PAGE = "page";

    private int page = PAGE_HOME;
    private NavCapsule nav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState != null) page = savedInstanceState.getInt(STATE_PAGE, PAGE_HOME);
        nav = installNav(page, index -> showPage(index, -1));
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.pageContainer, new HomeFragment(), TAGS[PAGE_HOME])
                    .commit();
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (page != PAGE_HOME) {
                    nav.select(PAGE_HOME, true);
                    showPage(PAGE_HOME, -1);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PAGE, page);
    }

    public void openMonitor(int tab) {
        nav.select(PAGE_MONITOR, true);
        showPage(PAGE_MONITOR, tab);
    }

    private Fragment create(int index, int tab) {
        if (index == PAGE_MONITOR) return MonitorFragment.newInstance(Math.max(tab, 0));
        if (index == PAGE_SETTINGS) return new SettingsFragment();
        return new HomeFragment();
    }

    private void showPage(int index, int tab) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment target = fm.findFragmentByTag(TAGS[index]);
        if (index == page && target != null) {
            if (tab >= 0 && target instanceof MonitorFragment) ((MonitorFragment) target).selectTab(tab);
            return;
        }
        Fragment current = fm.findFragmentByTag(TAGS[page]);
        boolean rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        boolean fromRight = (index > page) != rtl;
        FragmentTransaction t = fm.beginTransaction().setReorderingAllowed(true);
        if (!AppPreferences.isReduceMotion(this)) {
            if (fromRight) t.setCustomAnimations(R.anim.page_enter_from_right, R.anim.page_exit_to_left);
            else t.setCustomAnimations(R.anim.page_enter_from_left, R.anim.page_exit_to_right);
        }
        if (current != null) t.hide(current);
        if (target == null) {
            t.add(R.id.pageContainer, create(index, tab), TAGS[index]);
        } else {
            t.show(target);
            if (tab >= 0 && target instanceof MonitorFragment) ((MonitorFragment) target).selectTab(tab);
        }
        t.commit();
        page = index;
    }
}
