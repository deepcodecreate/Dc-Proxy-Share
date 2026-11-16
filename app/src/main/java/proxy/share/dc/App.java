/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import android.app.Application;
import proxy.share.dc.monitor.MonitorHub;

public class App extends Application {
    public static volatile android.content.Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        LogStore.init(this);
        MonitorHub.init(this);
        LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_APP, R.string.log_app_started);
    }
}
