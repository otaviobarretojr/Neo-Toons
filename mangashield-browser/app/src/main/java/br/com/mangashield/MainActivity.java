package br.com.mangashield;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Manga-first browser with in-app blocking, private bookmarks, and distraction-free reading. */
public final class MainActivity extends Activity {
    private static final String HOME = "https://neotoons.com.br/obras";
    private static final int BG = Color.rgb(11, 20, 33);
    private static final int PANEL = Color.rgb(20, 33, 51);
    private static final int TINT = Color.rgb(39, 211, 180);
    private static final int WHITE = Color.rgb(239, 246, 255);
    private static final int MUTED = Color.rgb(159, 176, 196);

    private SharedPreferences prefs;
    private AdBlocker blocker;
    private LinearLayout root;
    private LinearLayout topPanel;
    private LinearLayout bottomPanel;
    private FrameLayout browserFrame;
    private WebView browser;
    private EditText address;
    private ProgressBar progress;
    private TextView shieldButton;
    private TextView favoriteButton;
    private TextView exitReader;
    private volatile boolean protectionEnabled;
    private volatile String currentSite = "";
    private volatile Set<String> allowedSites = new HashSet<>();
    private final AtomicInteger blockedPage = new AtomicInteger();
    private final AtomicInteger blockedSession = new AtomicInteger();
    private boolean readerMode = false;
    private String cosmeticScript = "";
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs = getSharedPreferences("mangashield", MODE_PRIVATE);
        protectionEnabled = prefs.getBoolean("enabled", true);
        allowedSites = readAllowedSites();
        blocker = new AdBlocker(this, prefs);
        cosmeticScript = readAsset("clean.js");
        createLayout();
        configureWebView();
        blocker.start((count, updated) -> runOnUiThread(() -> updateShield()));
        if (state != null) browser.restoreState(state);
        else navigate(prefs.getString("last_url", HOME));
    }

    private void createLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().getDecorView().setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }

        topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.VERTICAL);
        topPanel.setPadding(dp(12), dp(9), dp(12), dp(8));
        topPanel.setBackgroundColor(BG);

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = label("⬡", 26, TINT, true);
        brand.addView(mark, new LinearLayout.LayoutParams(dp(29), dp(36)));
        TextView name = label("MangaShield", 19, WHITE, true);
        brand.addView(name, new LinearLayout.LayoutParams(0, dp(36), 1f));
        shieldButton = tap("⬡ 0", () -> showMenu(), dp(78));
        brand.addView(shieldButton);
        topPanel.addView(brand);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(0, dp(6), 0, 0);
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(13);
        address.setTextColor(WHITE);
        address.setHintTextColor(MUTED);
        address.setHint("Cole o link do mangá ou pesquise");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setSelectAllOnFocus(true);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setPadding(dp(13), 0, dp(11), 0);
        address.setBackground(rounded(PANEL, 14));
        nav.addView(address, new LinearLayout.LayoutParams(0, dp(45), 1f));
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(address.getText().toString());
                return true;
            }
            return false;
        });
        TextView go = tap("→", () -> navigate(address.getText().toString()), dp(44));
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(dp(44), dp(45));
        goParams.leftMargin = dp(8);
        nav.addView(go, goParams);
        topPanel.addView(nav);
        root.addView(topPanel);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(TINT));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(BG));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        progress.setVisibility(View.GONE);

        browserFrame = new FrameLayout(this);
        browser = new WebView(this);
        browser.setBackgroundColor(Color.WHITE);
        browserFrame.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        root.addView(browserFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        exitReader = tap("✕  Sair da leitura", () -> setReaderMode(false), dp(151));
        FrameLayout.LayoutParams exitParams = new FrameLayout.LayoutParams(dp(151), dp(46), Gravity.TOP | Gravity.RIGHT);
        exitParams.setMargins(dp(8), dp(12), dp(12), 0);
        browserFrame.addView(exitReader, exitParams);
        exitReader.setVisibility(View.GONE);

        bottomPanel = new LinearLayout(this);
        bottomPanel.setPadding(dp(8), dp(7), dp(8), dp(9));
        bottomPanel.setGravity(Gravity.CENTER);
        bottomPanel.setBackgroundColor(BG);
        addBottomButton("‹", "Voltar", () -> { if (browser.canGoBack()) browser.goBack(); });
        addBottomButton("›", "Avançar", () -> { if (browser.canGoForward()) browser.goForward(); });
        addBottomButton("⟳", "Atualizar", () -> browser.reload());
        favoriteButton = addBottomButton("☆", "Salvar favorito", () -> toggleFavorite());
        addBottomButton("⛶", "Modo leitura", () -> setReaderMode(true));
        addBottomButton("☰", "Opções", () -> showMenu());
        root.addView(bottomPanel);
    }

    private void configureWebView() {
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);
        WebView.setWebContentsDebuggingEnabled(false);

        browser.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                currentSite = AdBlocker.host(url);
                blockedPage.set(0);
                address.setText(url);
                updateShield();
                progress.setVisibility(View.VISIBLE);
                updateFavorite();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
                    prefs.edit().putString("last_url", url).apply();
                    address.setText(url);
                }
                if (protectionEnabled && !siteAllowed()) injectCosmetic();
                updateShield();
                updateFavorite();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
                    return true;
                }
                return request.isForMainFrame() && protectionEnabled &&
                    !siteAllowed() && blocker.isAdHost(AdBlocker.host(uri.toString()));
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return intercept(req);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    runOnUiThread(() -> toast("Falha na conexão. Confira sua internet ou o endereço."));
                }
            }
        });

        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog,
                    boolean isUserGesture, Message resultMsg) {
                // Denies JavaScript windows and target=_blank popup windows.
                return false;
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                request.deny(); // No camera, microphone or geolocation permissions for websites.
            }
        });

        if (Build.VERSION.SDK_INT >= 24) {
            ServiceWorkerController controller = ServiceWorkerController.getInstance();
            controller.setServiceWorkerClient(new ServiceWorkerClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return intercept(request);
                }
            });
        }
    }

    private WebResourceResponse intercept(WebResourceRequest req) {
        if (req.isForMainFrame() || !protectionEnabled) return null;
        if (isAllowed(currentSite)) return null;
        if (!blocker.shouldBlock(req.getUrl().toString())) return null;
        int n = blockedPage.incrementAndGet();
        blockedSession.incrementAndGet();
        if (n <= 4 || n % 6 == 0) ui.post(this::updateShield);
        return new WebResourceResponse("text/plain", "UTF-8",
            new ByteArrayInputStream(new byte[0]));
    }

    private void injectCosmetic() {
        if (!cosmeticScript.isEmpty()) browser.evaluateJavascript(cosmeticScript, null);
    }

    private String readAsset(String name) {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = getAssets().open(name);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) sb.append(buffer, 0, read);
        } catch (Exception ignored) { }
        return sb.toString();
    }

    private void navigate(String typed) {
        String query = typed == null ? "" : typed.trim();
        if (query.isEmpty()) return;
        String url;
        if (query.startsWith("https://") || query.startsWith("http://")) {
            url = query;
        } else if (!query.contains(" ") && query.contains(".") && !query.startsWith("about:")) {
            url = "https://" + query;
        } else {
            url = "https://www.google.com/search?q=" +
                URLEncoder.encode(query, StandardCharsets.UTF_8);
        }
        address.clearFocus();
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(address.getWindowToken(), 0);
        browser.loadUrl(url);
    }

    private void showMenu() {
        String host = currentSite.isEmpty() ? "site atual" : currentSite;
        String allowedLabel = siteAllowed() ? "Ativar bloqueio em " + host : "Desativar bloqueio em " + host;
        String[] options = {
            protectionEnabled ? "✓ Proteção geral: ligada" : "○ Proteção geral: desligada",
            allowedLabel,
            "Atualizar listas de bloqueio",
            "Meus favoritos",
            "Ir para NeoToons",
            "Abrir no navegador padrão",
            "Limpar cookies e histórico",
            "Sobre o MangaShield"
        };
        new AlertDialog.Builder(this).setTitle("Proteção e opções")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        protectionEnabled = !protectionEnabled;
                        prefs.edit().putBoolean("enabled", protectionEnabled).apply();
                        browser.reload();
                        updateShield();
                        break;
                    case 1:
                        toggleSiteException();
                        break;
                    case 2:
                        toast("Atualizando listas em segundo plano...");
                        blocker.updateNow((count, updated) -> runOnUiThread(() -> {
                            updateShield();
                            toast(updated ? "Filtros atualizados: " + count + " domínios" :
                                "Não foi possível atualizar. Filtros anteriores mantidos.");
                        }));
                        break;
                    case 3: showFavorites(); break;
                    case 4: navigate(HOME); break;
                    case 5: openExternal(); break;
                    case 6: confirmClear(); break;
                    case 7:
                        new AlertDialog.Builder(this).setTitle("MangaShield 1.0")
                            .setMessage("Navegador Android focado em mangás.\\n\\n" +
                                "Bloqueio por domínios, filtros cosméticos, anti-pop-up, " +
                                "favoritos locais e leitura em tela cheia.\\n\\n" +
                                "Bloqueados nesta sessão: " + blockedSession.get() +
                                "\\nDomínios na lista: " + blocker.count() +
                                "\\n\\nNão há como garantir 100% de bloqueio em todos os sites.")
                            .setPositiveButton("OK", null).show();
                        break;
                    default: break;
                }
            }).show();
    }

    private void openExternal() {
        String url = browser.getUrl();
        if (url == null || !url.startsWith("http")) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException ignored) { toast("Navegador externo não encontrado."); }
    }

    private Set<String> readAllowedSites() {
        return new HashSet<>(prefs.getStringSet("allowed_sites", new HashSet<>()));
    }

    private boolean isAllowed(String host) {
        for (String allowed : allowedSites) {
            if (AdBlocker.hostMatches(host, allowed)) return true;
        }
        return false;
    }

    private boolean siteAllowed() { return isAllowed(currentSite); }

    private void toggleSiteException() {
        if (currentSite.isEmpty()) { toast("Abra um site primeiro."); return; }
        Set<String> updated = new HashSet<>(allowedSites);
        if (updated.contains(currentSite)) updated.remove(currentSite);
        else updated.add(currentSite);
        allowedSites = updated;
        prefs.edit().putStringSet("allowed_sites", updated).apply();
        browser.reload();
        toast(updated.contains(currentSite) ? "Exceção ativada para este site." :
            "Proteção ativada para este site.");
    }

    private JSONArray bookmarks() {
        try { return new JSONArray(prefs.getString("bookmarks", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private boolean isFavorite(String url) {
        JSONArray b = bookmarks();
        for (int i=0; i < b.length(); i++) {
            JSONObject obj = b.optJSONObject(i);
            if (obj != null && url != null && url.equals(obj.optString("url"))) return true;
        }
        return false;
    }

    private void toggleFavorite() {
        String url = browser.getUrl();
        if (url == null || !url.startsWith("http")) return;
        JSONArray old = bookmarks(), newer = new JSONArray();
        boolean removed = false;
        for (int i=0; i<old.length(); i++) {
            JSONObject obj = old.optJSONObject(i);
            if (obj == null) continue;
            if (url.equals(obj.optString("url"))) removed = true;
            else newer.put(obj);
        }
        if (!removed) {
            JSONObject entry = new JSONObject();
            try {
                entry.put("url", url);
                String title = browser.getTitle();
                entry.put("title", title == null || title.trim().isEmpty() ? AdBlocker.host(url) : title);
                newer.put(entry);
            } catch (Exception ignored) { }
        }
        prefs.edit().putString("bookmarks", newer.toString()).apply();
        updateFavorite();
        toast(removed ? "Removido dos favoritos" : "Página salva nos favoritos");
    }

    private void showFavorites() {
        JSONArray data = bookmarks();
        if (data.length() == 0) { toast("Seus favoritos aparecerão aqui."); return; }
        String[] titles = new String[data.length()];
        for (int i=0; i<data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            titles[i] = item == null ? "Favorito" : item.optString("title", "Favorito");
        }
        new AlertDialog.Builder(this).setTitle("Meus favoritos")
            .setItems(titles, (dialog, which) -> {
                JSONObject item = data.optJSONObject(which);
                if (item != null) navigate(item.optString("url", HOME));
            }).setNegativeButton("Fechar", null).show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("Limpar navegação?")
            .setMessage("Remove cookies, cache e histórico do navegador. Favoritos serão mantidos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Limpar", (dialog, which) -> {
                browser.clearCache(true);
                browser.clearHistory();
                CookieManager.getInstance().removeAllCookies(null);
                android.webkit.WebStorage.getInstance().deleteAllData();
                prefs.edit().remove("last_url").apply();
                toast("Dados de navegação limpos.");
            }).show();
    }

    private void setReaderMode(boolean active) {
        readerMode = active;
        topPanel.setVisibility(active ? View.GONE : View.VISIBLE);
        bottomPanel.setVisibility(active ? View.GONE : View.VISIBLE);
        exitReader.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void updateShield() {
        if (shieldButton == null) return;
        String text = protectionEnabled ? "⬡ " + blockedPage.get() : "○ OFF";
        shieldButton.setText(text);
        shieldButton.setTextColor(protectionEnabled && !siteAllowed() ? TINT : MUTED);
    }

    private void updateFavorite() {
        if (favoriteButton != null) {
            favoriteButton.setText(isFavorite(browser.getUrl()) ? "★" : "☆");
        }
    }

    private TextView addBottomButton(String symbol, String description, Runnable action) {
        TextView t = label(symbol, 24, WHITE, false);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(description);
        t.setOnClickListener(v -> action.run());
        bottomPanel.addView(t, new LinearLayout.LayoutParams(0, dp(42), 1f));
        return t;
    }

    private TextView label(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView tap(String text, Runnable action, int width) {
        TextView t = label(text, 14, WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(PANEL, 14));
        t.setOnClickListener(v -> action.run());
        t.setContentDescription(text);
        t.setLayoutParams(new LinearLayout.LayoutParams(width, dp(40)));
        return t;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int dp(int n) { return (int) (getResources().getDisplayMetrics().density * n + .5f); }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    @Override public void onBackPressed() {
        if (readerMode) setReaderMode(false);
        else if (browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        browser.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        blocker.shutdown();
        browser.stopLoading();
        browser.destroy();
        super.onDestroy();
    }
}
