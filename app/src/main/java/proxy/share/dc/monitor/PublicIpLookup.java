/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.monitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public final class PublicIpLookup {
    public static final class Info {
        public String ip = "";
        public String country = "";
        public String countryCode = "";
        public String region = "";
        public String city = "";
        public String isp = "";
        public String org = "";
        public String asn = "";
        public String timezone = "";
        public long fetchedAt;
    }

    private PublicIpLookup() {
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        try {
            if (c.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    public static Info fetch() {
        try {
            String body = get("https://ipwho.is/");
            if (body != null) {
                JSONObject o = new JSONObject(body);
                if (o.optBoolean("success", false)) {
                    Info i = new Info();
                    i.ip = o.optString("ip");
                    i.country = o.optString("country");
                    i.countryCode = o.optString("country_code");
                    i.region = o.optString("region");
                    i.city = o.optString("city");
                    JSONObject conn = o.optJSONObject("connection");
                    if (conn != null) {
                        i.isp = conn.optString("isp");
                        i.org = conn.optString("org");
                        i.asn = conn.optString("asn");
                    }
                    JSONObject tz = o.optJSONObject("timezone");
                    if (tz != null) i.timezone = tz.optString("id");
                    i.fetchedAt = System.currentTimeMillis();
                    return i;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            String body = get("https://api.ipify.org?format=json");
            if (body != null) {
                Info i = new Info();
                i.ip = new JSONObject(body).optString("ip");
                i.fetchedAt = System.currentTimeMillis();
                return i.ip.isEmpty() ? null : i;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
