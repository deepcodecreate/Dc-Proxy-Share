/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.card.MaterialCardView;
import java.util.HashMap;
import java.util.Map;
import proxy.share.dc.R;

public class InfoCard extends MaterialCardView {
    private final LinearLayout body;
    private final ImageButton action;
    private final Map<String, TextView> values = new HashMap<>();
    private final Map<String, View> rows = new HashMap<>();

    public InfoCard(Context context, int titleRes, String iconName) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.view_info_card, this, true);
        body = findViewById(R.id.infoBody);
        action = findViewById(R.id.infoAction);
        TextView title = findViewById(R.id.infoTitle);
        ImageView icon = findViewById(R.id.infoIcon);
        title.setText(titleRes);
        icon.setImageDrawable(UiKit.icon(context, iconName));
    }

    public void setAction(String iconName, int descriptionRes, View.OnClickListener l) {
        Drawable d = UiKit.icon(getContext(), iconName);
        action.setImageDrawable(d);
        action.setContentDescription(getContext().getString(descriptionRes));
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            l.onClick(v);
        });
    }

    public TextView row(String key, int labelRes) {
        return row(key, getContext().getString(labelRes));
    }

    public TextView row(String key, CharSequence labelText) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.item_info_row, body, false);
        TextView label = row.findViewById(R.id.rowLabel);
        TextView value = row.findViewById(R.id.rowValue);
        label.setText(labelText);
        value.setText("—");
        body.addView(row);
        values.put(key, value);
        rows.put(key, row);
        return value;
    }

    public void addCustom(View v) {
        UiKit.addSpaced(body, v, 8);
    }

    public LinearLayout body() {
        return body;
    }

    public void set(String key, CharSequence text) {
        TextView v = values.get(key);
        if (v != null) {
            CharSequence current = v.getText();
            if (!current.toString().contentEquals(text)) v.setText(text);
        }
    }

    public void set(String key, CharSequence text, int color) {
        set(key, text);
        TextView v = values.get(key);
        if (v != null) v.setTextColor(color);
    }

    public void reset(String key) {
        TextView v = values.get(key);
        if (v != null) {
            v.setText("—");
            v.setTextColor(UiKit.onSurface(getContext()));
        }
    }

    public void showRow(String key, boolean show) {
        View r = rows.get(key);
        if (r != null) r.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public void setValueCopyable(String key, String label) {
        TextView v = values.get(key);
        if (v != null) {
            v.setOnClickListener(x -> {
                CharSequence t = v.getText();
                if (t.length() > 1) UiKit.copy(getContext(), label, t.toString());
            });
        }
    }
}
