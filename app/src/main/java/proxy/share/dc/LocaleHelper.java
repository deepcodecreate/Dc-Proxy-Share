/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;
import proxy.share.dc.ui.AppPreferences;

public class LocaleHelper {
    public static Context setLocale(Context context) {
        Locale locale = new Locale(AppPreferences.getLanguage(context));
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        config.fontScale = config.fontScale * AppPreferences.getFontScale(context);
        return context.createConfigurationContext(config);
    }
}
