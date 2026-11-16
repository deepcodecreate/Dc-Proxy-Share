/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.monitor;

import android.content.Context;
import proxy.share.dc.R;
import proxy.share.dc.ui.UiKit;

public final class Quality {
    private Quality() {
    }

    public static int labelRes(float ms) {
        if (ms < 0) return R.string.quality_none;
        if (ms < 50) return R.string.quality_excellent;
        if (ms < 100) return R.string.quality_good;
        if (ms < 200) return R.string.quality_fair;
        return R.string.quality_poor;
    }

    public static int color(Context c, float ms) {
        if (ms < 0) return UiKit.error(c);
        if (ms < 100) return UiKit.success(c);
        if (ms < 200) return UiKit.warning(c);
        return UiKit.error(c);
    }

    public static int signalColor(Context c, int percent) {
        if (percent >= 60) return UiKit.success(c);
        if (percent >= 35) return UiKit.warning(c);
        return UiKit.error(c);
    }
}
