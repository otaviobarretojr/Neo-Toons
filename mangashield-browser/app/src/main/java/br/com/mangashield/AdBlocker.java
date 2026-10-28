package br.com.mangashield;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Lightweight host-based content blocker; filters only requests within this browser.
 * Remote sources are public hosts lists (not uBlock/EasyList syntax).
 */
public final class AdBlocker {
    public interface Listener { void finished(int domainCount, boolean updated); }

    private static final String[] SOURCES = {
        "https://adaway.org/hosts.txt",
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    };
    private static final String[] BUILTIN = {
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "adservice.google.com", "adnxs.com", "adsrvr.org",
        "taboola.com", "outbrain.com", "criteo.com", "criteo.net",
        "pubmatic.com", "rubiconproject.com", "openx.net",
        "smartadserver.com", "advertising.com", "quantserve.com",
        "moatads.com", "scorecardresearch.com", "yieldmo.com",
        "casalemedia.com", "lijit.com", "sharethrough.com",
        "zedo.com", "popads.net", "popcash.net", "propellerads.com",
        "onclickads.net", "onclicka.com", "popunder.net",
        "exoclick.com", "exosrv.com", "trafficjunky.net",
        "juicyads.com", "adsterra.com", "ad-maven.com",
        "mgid.com", "revcontent.com", "bidvertiser.com",
        "adform.net", "bidswitch.net", "indexww.com",
        "media.net", "amazon-adsystem.com", "adsafeprotected.com",
        "adskeeper.co.uk", "adblade.com", "sovrn.com",
        "contextweb.com", "onetag-sys.com", "teads.tv",
        "tpc.googlesyndication.com", "ad.doubleclick.net",
        "static.doubleclick.net", "pagead2.googlesyndication.com",
        "googletagservices.com", "track.adform.net", "analytics.tiktok.com",
        "ads-twitter.com", "pixel.facebook.com", "bat.bing.com",
        "clickadu.com", "hilltopads.net", "monetag.com",
        "pushground.com", "adpushup.com", "clickaine.com",
        "histats.com", "hotjar.com", "luckyorange.com",
        "sentry.io", "newrelic.com"
    };
    private static final Pattern AD_PATH = Pattern.compile(
        "(?i)(?:^|/)(?:ads?|advertisements?|popunders?|prebid|bannerads|adsystem)(?:/|[.?_=-]|$)"
    );
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_SOURCE_CHARS = 16 * 1024 * 1024;

    private final Context app;
    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Set<String> domains = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(BUILTIN)));

    public AdBlocker(Context context, SharedPreferences prefs) {
        app = context.getApplicationContext();
        preferences = prefs;
    }

    public int count() { return domains.size(); }

    public void start(Listener listener) {
        executor.execute(() -> {
            loadCache();
            if (System.currentTimeMillis() - preferences.getLong("lists_updated", 0) > WEEK_MS) {
                updateNowInternal(listener);
            } else if (listener != null) {
                listener.finished(domains.size(), false);
            }
        });
    }

    public void updateNow(Listener listener) {
        executor.execute(() -> updateNowInternal(listener));
    }

    private void loadCache() {
        File file = new File(app.getFilesDir(), "blocked_hosts.txt");
        if (!file.exists()) return;
        Set<String> loaded = new HashSet<>(Arrays.asList(BUILTIN));
        try (InputStream input = new FileInputStream(file)) {
            parse(input, loaded);
            if (loaded.size() > 200) domains = Collections.unmodifiableSet(loaded);
        } catch (Exception ignored) { /* bundled rules remain active */ }
    }

    private void updateNowInternal(Listener listener) {
        Set<String> downloaded = new HashSet<>(domains);
        int goodSources = 0;
        for (String address : SOURCES) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(20000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "MangaShield/1.0");
                if (connection.getResponseCode() != 200) continue;
                Set<String> partial = new HashSet<>();
                try (InputStream stream = connection.getInputStream()) {
                    parse(stream, partial);
                }
                if (partial.size() < 1000) continue;
                downloaded.addAll(partial);
                goodSources++;
            } catch (Exception ignored) {
                // Offline use continues with prior cache and embedded rules.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        if (goodSources > 0) {
            domains = Collections.unmodifiableSet(downloaded);
            saveCache(downloaded);
            preferences.edit().putLong("lists_updated", System.currentTimeMillis()).apply();
        }
        if (listener != null) listener.finished(domains.size(), goodSources > 0);
    }

    private void parse(InputStream input, Set<String> destination) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        int chars = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            chars += line.length() + 1;
            if (chars > MAX_SOURCE_CHARS) break;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
            int comment = line.indexOf('#');
            if (comment != -1) line = line.substring(0, comment).trim();
            String[] tokens = line.split("\\s+");
            if (tokens.length == 0) continue;
            int first = (tokens[0].equals("0.0.0.0") ||
                         tokens[0].equals("127.0.0.1") ||
                         tokens[0].equals("::1")) ? 1 : 0;
            for (int i = first; i < tokens.length; i++) {
                String host = tokens[i].toLowerCase(Locale.ROOT).trim();
                if (host.length() < 4 || host.length() > 250 ||
                    host.equals("localhost") || host.contains("/") ||
                    host.contains(":") || !host.contains(".")) continue;
                if (host.matches("[a-z0-9][a-z0-9._-]*[a-z0-9]")) {
                    destination.add(host);
                }
            }
        }
    }

    private void saveCache(Set<String> values) {
        File tmp = new File(app.getFilesDir(), "blocked_hosts.tmp");
        File target = new File(app.getFilesDir(), "blocked_hosts.txt");
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            for (String s : values) { writer.write(s); writer.newLine(); }
            writer.flush();
            if (!tmp.renameTo(target)) {
                // Some filesystems will not replace an existing file with renameTo.
                if (target.delete()) tmp.renameTo(target);
            }
        } catch (Exception ignored) { tmp.delete(); }
    }

    public static String host(String url) {
        try {
            String h = Uri.parse(url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    public boolean isAdHost(String host) {
        if (host == null || host.isEmpty()) return false;
        Set<String> snapshot = domains;
        for (String segment = host; !segment.isEmpty();) {
            if (snapshot.contains(segment)) return true;
            int dot = segment.indexOf('.');
            if (dot < 0) break;
            segment = segment.substring(dot + 1);
        }
        return false;
    }

    public static boolean hostMatches(String host, String base) {
        return !base.isEmpty() && (host.equals(base) || host.endsWith("." + base));
    }

    public boolean shouldBlock(String url) {
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
            String host = uri.getHost();
            if (host == null) return false;
            if (isAdHost(host.toLowerCase(Locale.ROOT))) return true;
            String path = uri.getEncodedPath();
            return path != null && AD_PATH.matcher(path).find();
        } catch (Exception ignored) { return false; }
    }

    public void shutdown() { executor.shutdownNow(); }
}
