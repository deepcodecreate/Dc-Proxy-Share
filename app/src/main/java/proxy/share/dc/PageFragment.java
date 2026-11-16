/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import proxy.share.dc.ui.BaseActivity;
import proxy.share.dc.ui.ShapeView;
import proxy.share.dc.ui.UiKit;

public abstract class PageFragment extends Fragment {
    protected Context ctx;
    protected View root;
    private boolean active;

    protected abstract int layoutRes();

    protected abstract void onViewReady(Bundle savedInstanceState);

    protected void onActive() {
    }

    protected void onInactive() {
    }

    protected BaseActivity host() {
        return (BaseActivity) requireActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ctx = requireContext();
        root = inflater.inflate(layoutRes(), container, false);
        onViewReady(savedInstanceState);
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!isHidden()) setActive(true);
    }

    @Override
    public void onStop() {
        setActive(false);
        super.onStop();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (isAdded() && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            setActive(!hidden);
        }
    }

    private void setActive(boolean a) {
        if (a == active) return;
        active = a;
        if (a) onActive();
        else onInactive();
    }

    protected void bindHeader(int titleRes, CharSequence subtitle, String iconName) {
        TextView title = root.findViewById(R.id.headerTitle);
        TextView sub = root.findViewById(R.id.headerSubtitle);
        ShapeView logo = root.findViewById(R.id.headerLogo);
        title.setText(titleRes);
        sub.setText(subtitle);
        logo.setFillColor(UiKit.color(ctx, "colorPrimaryContainer", 0xFF444444));
        logo.setIcon(UiKit.icon(ctx, iconName), UiKit.color(ctx, "colorOnPrimaryContainer", 0xFFFFFFFF));
        logo.setIconFraction(0.5f);
    }

    protected void setHeaderSubtitle(CharSequence s) {
        TextView sub = root.findViewById(R.id.headerSubtitle);
        if (sub != null && !sub.getText().toString().contentEquals(s)) sub.setText(s);
    }
}
