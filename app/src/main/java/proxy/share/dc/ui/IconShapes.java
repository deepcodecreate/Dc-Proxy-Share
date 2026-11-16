/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.ui;

import android.graphics.Path;
import proxy.share.dc.R;

public final class IconShapes {
    public static final String[] IDS = {"squircle", "circle", "cookie4", "cookie7", "flower", "hexagon", "pebble"};
    private static final int STEPS = 180;

    private IconShapes() {
    }

    public static String normalize(String id) {
        for (String s : IDS) {
            if (s.equals(id)) return id;
        }
        return IDS[0];
    }

    public static int labelRes(String id) {
        switch (id) {
            case "circle":
                return R.string.shape_circle;
            case "cookie4":
                return R.string.shape_cookie4;
            case "cookie7":
                return R.string.shape_cookie7;
            case "flower":
                return R.string.shape_flower;
            case "hexagon":
                return R.string.shape_hexagon;
            case "pebble":
                return R.string.shape_pebble;
            default:
                return R.string.shape_squircle;
        }
    }

    private static double radius(String id, double a) {
        switch (id) {
            case "circle":
                return 1.0;
            case "cookie4":
                return 0.86 + 0.14 * Math.cos(4 * a);
            case "cookie7":
                return 0.88 + 0.12 * Math.cos(7 * a);
            case "flower":
                return 0.80 + 0.20 * Math.cos(8 * a);
            case "hexagon": {
                double s = Math.PI / 3;
                double m = ((a % s) + s) % s;
                double poly = Math.cos(Math.PI / 6) / Math.cos(m - Math.PI / 6);
                return 0.8 * poly + 0.2;
            }
            case "pebble":
                return 0.9 + 0.06 * Math.cos(2 * a + 0.6) + 0.04 * Math.cos(3 * a);
            default: {
                double c = Math.abs(Math.cos(a));
                double s = Math.abs(Math.sin(a));
                double r = Math.pow(Math.pow(c, 4) + Math.pow(s, 4), -0.25);
                return r / 1.1892071;
            }
        }
    }

    public static void build(Path path, String id, float cx, float cy, float radius) {
        path.reset();
        String shape = normalize(id);
        for (int i = 0; i <= STEPS; i++) {
            double a = (Math.PI * 2 * i) / STEPS;
            double r = radius(shape, a) * radius;
            float x = (float) (cx + Math.cos(a) * r);
            float y = (float) (cy + Math.sin(a) * r);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
    }
}
