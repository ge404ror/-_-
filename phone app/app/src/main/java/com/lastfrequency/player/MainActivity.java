package com.lastfrequency.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements PlaybackService.Listener {
    private static final int REQUEST_FILES = 41;
    private static final int REQUEST_FOLDER = 42;
    private static final int REQUEST_NOTIFICATIONS = 43;
    private static final int REQUEST_DOWNLOAD_FOLDER = 44;
    private static final String PREFS = "last_frequency_phone";
    private static final String KEY_LIBRARY = "library_v1";
    private static final String KEY_PLAYLISTS = "playlists_v1";
    private static final String KEY_RECENT = "recently_played_v1";
    private static final String KEY_DOWNLOAD_FOLDER = "download_folder_uri";
    private static final String KEY_ONLINE_SOURCES = "online_sources";
    private static final String[] ONLINE_SOURCE_KEYS = {"youtube_music", "youtube", "soundcloud", "deezer", "apple"};
    private static final String[] ONLINE_SOURCE_NAMES = {"YouTube Music", "YouTube", "SoundCloud", "Deezer", "Apple Music"};
    private static final String[] THEME_NAMES = {"Полночь", "Неон", "Океан", "Монохром", "Maretu", "Spotify", "Discord", "YouTube", "SoundCloud", "Bloodline", "Своя тема"};
    private static final String[][] THEME_PALETTES = {
            {"#0f1020", "#1a1d3a", "#2b1d47", "#16182e", "#eceef8", "#9aa0bd", "#8b95ff"},
            {"#07020f", "#1a0633", "#04202e", "#140824", "#f5eaff", "#a98fd0", "#ff3df0"},
            {"#04121c", "#06324a", "#0a4b63", "#062436", "#e6f6ff", "#85b4cc", "#38bdf8"},
            {"#0a0a0a", "#161616", "#222222", "#141414", "#f2f2f2", "#9a9a9a", "#ffffff"},
            {"#000000", "#080808", "#171717", "#121212", "#f5f5f5", "#a6a6a6", "#f2f2f2"},
            {"#121212", "#191919", "#242424", "#181818", "#ffffff", "#b3b3b3", "#1ed760"},
            {"#1e1f22", "#242528", "#313338", "#232428", "#f2f3f5", "#b5bac1", "#5865f2"},
            {"#0f0f0f", "#181818", "#272727", "#181818", "#ffffff", "#aaaaaa", "#ff0033"},
            {"#160f0a", "#21150e", "#302014", "#201710", "#fff7f0", "#c5a996", "#ff5500"},
            {"#100306", "#27070e", "#4a0b17", "#23070e", "#fff0f2", "#cc929b", "#ff2438"},
            {"#12131c", "#1a1d3a", "#2b1d47", "#16182e", "#eceef8", "#9aa0bd", "#8b95ff"}
    };

    private final ArrayList<Track> tracks = new ArrayList<>();
    private final ArrayList<Playlist> playlists = new ArrayList<>();
    private final ArrayList<Track> onlineResults = new ArrayList<>();
    private final ArrayList<Track> recentlyPlayed = new ArrayList<>();
    private final ExecutorService importer = Executors.newSingleThreadExecutor();
    private final ExecutorService onlineSearchWorker = Executors.newFixedThreadPool(2);
    private final ExecutorService coverLoader = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> artworkCache = new LruCache<String, Bitmap>(24 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return Math.max(1, bitmap.getByteCount() / 1024); }
    };

    private SharedPreferences preferences;
    private PlaybackService playback;
    private boolean bound;
    private int page = 0;
    private int selectedTheme;
    private int selectedSort;
    private String selectedPlaylist = "";
    private String query = "";
    private String onlineQuery = "";
    private String onlineProvider = "all";
    private final LinkedHashSet<String> selectedOnlineProviders = new LinkedHashSet<>();
    private boolean onlineBusy;
    private boolean onlineSearchBusy;
    private boolean showingRecentlyPlayed;
    private boolean onlineEngineReady;
    private String onlineEngineError = "";
    private int backgroundColor, surfaceColor, cardColor, textColor, mutedColor, accentColor;
    private int[] gradientColors;
    private LinearLayout root, topTabs, playerDock;
    private LinearLayout mainBodyContent;
    private ScrollView mainBodyScroll;
    private View mainTabScroll;
    private EditText searchField;
    private EditText onlineSearchField;
    private TextView onlineStatus;
    private ListView onlineList;
    private FrameLayout pageHost;
    private TextView trackTitle, trackArtist, playerPlay;
    private ImageView playerCover;
    private SeekBar dockSeek, dockVolume;
    private TextView dockTime;
    private Dialog nowPlayingDialog;
    private FrameLayout nowPlayingFrame;
    private ImageView nowPlayingCover;
    private TextView nowPlayingLyricsStatus;
    private LinearLayout nowPlayingLyricsLines;
    private ScrollView nowPlayingLyricsScroll;
    private SeekBar nowPlayingSeek;
    private TextView nowPlayingTime;
    private TextView nowPlayingTitle, nowPlayingArtist;
    private Button nowPlayingPlayButton;
    private final ArrayList<LyricLine> nowPlayingLyrics = new ArrayList<>();
    private final Handler playerUiHandler = new Handler(Looper.getMainLooper());
    private Runnable playerUiTicker;
    private boolean nowPlayingShowLyrics;
    private boolean updatingSeekBars;
    private String nowPlayingLyricsKey = "";
    private int activeLyricLine = -1;
    private Track nowPlayingTrack;
    private Button tabTracks, tabOnline, tabPlaylists, tabSettings;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingOnlineSearch;
    private long onlineSearchSequence;
    private String onlineKind = "track";
    private String currentOnlinePlaylistTitle = "";
    private final ArrayList<Track> previousOnlineResults = new ArrayList<>();
    private TextView onlineEmpty;
    private TextView onlineResultTitle;
    private Button onlineBack;
    private Button onlineKindTracks, onlineKindPlaylists;
    private Button[] onlineProviderButtons;

    private static final class Track {
        String uri, title, artist, album;
        String provider = "", artworkUrl = "", previewUrl = "";
        boolean online;
        boolean previewOnly;
        boolean isPlaylist;
        int trackCount;
        int duration;
        int orderNumber;
        Track(String uri, String title, String artist, String album) {
            this.uri = uri; this.title = title; this.artist = artist; this.album = album;
        }
        Track(String uri, String title, String artist, String album, String provider, String artworkUrl) {
            this(uri, title, artist, album);
            this.provider = provider == null ? "" : provider;
            this.artworkUrl = artworkUrl == null ? "" : artworkUrl;
            this.online = true;
        }
    }

    private static final class Playlist {
        String name;
        final ArrayList<String> ids = new ArrayList<>();
        Playlist(String name) { this.name = name; }
    }

    private static final class LyricLine {
        final long timeMs;
        final String text;
        LyricLine(long timeMs, String text) { this.timeMs = timeMs; this.text = text; }
    }

    private static final class CoverBackgroundDrawable extends android.graphics.drawable.Drawable {
        private final Bitmap bitmap;
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int drawableAlpha = 255;

        CoverBackgroundDrawable(Bitmap bitmap) { this.bitmap = bitmap; }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty() || bitmap == null || bitmap.isRecycled()) return;
            float scale = Math.max((float) bounds.width() / bitmap.getWidth(), (float) bounds.height() / bitmap.getHeight());
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            RectF destination = new RectF(bounds.centerX() - width / 2f, bounds.centerY() - height / 2f,
                    bounds.centerX() + width / 2f, bounds.centerY() + height / 2f);
            int save = canvas.save();
            canvas.clipRect(bounds);
            imagePaint.setAlpha(Math.round(145f * drawableAlpha / 255f));
            canvas.drawBitmap(bitmap, null, destination, imagePaint);
            shadePaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                    new int[]{0x99000000, 0xB8000000, 0xD9000000}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(bounds, shadePaint);
            shadePaint.setShader(null);
            canvas.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) { drawableAlpha = Math.max(0, Math.min(255, alpha)); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter colorFilter) { imagePaint.setColorFilter(colorFilter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class ThemeIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private final int color;
        ThemeIconDrawable(float density, int color) { this.density = density; this.color = color; }
        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float cx = bounds.exactCenterX(), cy = bounds.exactCenterY();
            float radius = Math.min(bounds.width(), bounds.height()) * 0.24f;
            paint.setColor(color); paint.setStrokeWidth(2f * density); paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * i / 4d;
                float inner = radius + 2f * density, outer = radius + 6f * density;
                canvas.drawLine(cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                        cx + (float) Math.cos(angle) * outer, cy + (float) Math.sin(angle) * outer, paint);
            }
            RectF circle = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawArc(circle, 90, 180, true, paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(cx, cy, radius, paint);
            canvas.drawLine(cx, cy - radius + density, cx, cy + radius - density, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return Math.round(24 * density); }
        @Override public int getIntrinsicHeight() { return Math.round(24 * density); }
    }

    private static final class SpeakerIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private final int color;
        SpeakerIconDrawable(float density, int color) { this.density = density; this.color = color; }
        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float cx = bounds.exactCenterX(), cy = bounds.exactCenterY();
            paint.setColor(color); paint.setStyle(Paint.Style.FILL);
            android.graphics.Path speaker = new android.graphics.Path();
            speaker.moveTo(cx - 7 * density, cy - 3 * density);
            speaker.lineTo(cx - 3 * density, cy - 3 * density);
            speaker.lineTo(cx + 2 * density, cy - 7 * density);
            speaker.lineTo(cx + 2 * density, cy + 7 * density);
            speaker.lineTo(cx - 3 * density, cy + 3 * density);
            speaker.lineTo(cx - 7 * density, cy + 3 * density);
            speaker.close(); canvas.drawPath(speaker, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.6f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawArc(new RectF(cx - 2 * density, cy - 5 * density, cx + 6 * density, cy + 5 * density), -65, 130, false, paint);
            canvas.drawArc(new RectF(cx - 2 * density, cy - 8 * density, cx + 10 * density, cy + 8 * density), -65, 130, false, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return Math.round(18 * density); }
        @Override public int getIntrinsicHeight() { return Math.round(18 * density); }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            playback = ((PlaybackService.LocalBinder) binder).getService();
            playback.setListener(MainActivity.this);
            playback.setVolume(preferences.getInt("player_volume", 100));
            playback.setBassBoostX10(preferences.getInt("bass_boost_x10", 0));
            renderPlayerDock();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            if (playback != null) playback.setListener(null);
            playback = null;
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        selectedTheme = preferences.getInt("theme", 0);
        migrateThemeSelection();
        selectedTheme = Math.max(0, Math.min(THEME_NAMES.length - 1, selectedTheme));
        selectedSort = preferences.getInt("sort", 0);
        onlineProvider = preferences.getString("online_provider", "all");
        onlineKind = preferences.getString("online_kind", "track");
        if (!("all".equals(onlineProvider) || "youtube_music".equals(onlineProvider)
                || "youtube".equals(onlineProvider) || "soundcloud".equals(onlineProvider)
                || "deezer".equals(onlineProvider) || "apple".equals(onlineProvider))) onlineProvider = "all";
        String savedSources = preferences.getString(KEY_ONLINE_SOURCES, "");
        if (!savedSources.isEmpty()) {
            for (String key : savedSources.split(",")) for (String valid : ONLINE_SOURCE_KEYS)
                if (valid.equals(key.trim())) selectedOnlineProviders.add(valid);
        } else if ("all".equals(onlineProvider)) Collections.addAll(selectedOnlineProviders, ONLINE_SOURCE_KEYS);
        else if (isOnlineSource(onlineProvider)) selectedOnlineProviders.add(onlineProvider);
        if (selectedOnlineProviders.isEmpty()) {
            selectedOnlineProviders.add("youtube_music"); selectedOnlineProviders.add("youtube"); selectedOnlineProviders.add("soundcloud");
        }
        if (!("track".equals(onlineKind) || "playlist".equals(onlineKind))) onlineKind = "track";
        readLibrary();
        readPlaylists();
        readRecentlyPlayed();
        applyPalette();
        buildLayout();
        renderPage();
        renderPlayerDock();
        importer.execute(() -> {
            try { OnlineCatalog.initialize(getApplicationContext()); onlineEngineReady = true; }
            catch (Exception error) {
                onlineEngineError = error.getMessage() == null ? "Не удалось запустить онлайн-поиск" : error.getMessage();
                android.util.Log.e("LastFrequency", "Online engine could not start", error);
            }
            runOnUiThread(() -> {
                if (page == 1 && onlineStatus != null && !onlineSearchBusy && !onlineBusy) {
                    onlineStatus.setText(onlineEngineReady
                            ? "Поиск готов. Вставь ссылку или введи от двух символов."
                            : onlineEngineError);
                    if (onlineEngineReady && !onlineQuery.trim().isEmpty()) scheduleOnlineSearch(0);
                }
            });
        });
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        bound = bindService(new Intent(this, PlaybackService.class), serviceConnection, BIND_AUTO_CREATE);
    }

    @Override protected void onStop() {
        if (playback != null) playback.setListener(null);
        if (bound) { unbindService(serviceConnection); bound = false; }
        playback = null;
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (pendingOnlineSearch != null) searchHandler.removeCallbacks(pendingOnlineSearch);
        if (playerUiTicker != null) playerUiHandler.removeCallbacks(playerUiTicker);
        if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) nowPlayingDialog.dismiss();
        importer.shutdown();
        onlineSearchWorker.shutdownNow();
        coverLoader.shutdown();
        super.onDestroy();
    }

    @Override public void onPlaybackStateChanged(String title, String artist, String uri, boolean playing) {
        rememberRecentlyPlayed(title, artist, uri, playing);
        runOnUiThread(() -> {
            renderPlayerDock();
            updateTrackCoverBackground();
        });
    }

    private void rememberRecentlyPlayed(String title, String artist, String uri, boolean playing) {
        if (!playing || uri == null || uri.trim().isEmpty()) return;
        Track source = findAnyTrack(uri);
        if (source == null) for (Track recent : recentlyPlayed) if (recent.uri.equals(uri)) { source = recent; break; }
        Track item = new Track(uri, title == null || title.isEmpty() ? "Без названия" : title,
                artist == null || artist.isEmpty() ? "Неизвестный исполнитель" : artist,
                source == null ? "" : source.album,
                source == null ? "" : source.provider,
                source == null ? "" : source.artworkUrl);
        item.orderNumber = source == null ? 0 : source.orderNumber;
        item.online = source != null ? source.online : uri.startsWith("http://") || uri.startsWith("https://");
        item.previewOnly = source != null && source.previewOnly;
        item.previewUrl = source == null ? "" : source.previewUrl;
        item.duration = source == null ? 0 : source.duration;
        for (int i = recentlyPlayed.size() - 1; i >= 0; i--) {
            if (recentlyPlayed.get(i).uri.equals(uri)) recentlyPlayed.remove(i);
        }
        recentlyPlayed.add(0, item);
        while (recentlyPlayed.size() > 20) recentlyPlayed.remove(recentlyPlayed.size() - 1);
        saveRecentlyPlayed();
        if (page == 1 && onlineQuery.trim().isEmpty() && currentOnlinePlaylistTitle.isEmpty()) {
            showingRecentlyPlayed = true;
            onlineResults.clear(); onlineResults.addAll(recentlyPlayed);
            if (onlineStatus != null) onlineStatus.setText("Недавно прослушанные треки");
            updateOnlineSearchViews();
        }
    }

    private void readRecentlyPlayed() {
        recentlyPlayed.clear();
        try {
            JSONArray saved = new JSONArray(preferences.getString(KEY_RECENT, "[]"));
            for (int i = 0; i < saved.length() && recentlyPlayed.size() < 20; i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) continue;
                String uri = item.optString("uri", "");
                if (uri.isEmpty()) continue;
                Track track = new Track(uri, item.optString("title", "Без названия"),
                        item.optString("artist", "Неизвестный исполнитель"), item.optString("album", ""),
                        item.optString("provider", ""), item.optString("artworkUrl", ""));
                normalizeTrackMetadata(track);
                track.orderNumber = item.optInt("orderNumber", 0);
                track.online = item.optBoolean("online", uri.startsWith("http://") || uri.startsWith("https://"));
                track.previewOnly = item.optBoolean("previewOnly", false);
                track.previewUrl = item.optString("previewUrl", "");
                track.duration = item.optInt("duration", 0);
                recentlyPlayed.add(track);
            }
        } catch (Exception ignored) { }
    }

    private void saveRecentlyPlayed() {
        JSONArray saved = new JSONArray();
        try {
            for (Track track : recentlyPlayed) {
                JSONObject item = new JSONObject();
                item.put("uri", track.uri); item.put("title", track.title); item.put("artist", track.artist);
                item.put("album", track.album); item.put("provider", track.provider); item.put("artworkUrl", track.artworkUrl);
                item.put("online", track.online); item.put("previewOnly", track.previewOnly);
                item.put("previewUrl", track.previewUrl); item.put("duration", track.duration);
                item.put("orderNumber", track.orderNumber);
                saved.put(item);
            }
            preferences.edit().putString(KEY_RECENT, saved.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void buildLayout() {
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(true);
        getWindow().setStatusBarColor(backgroundColor);
        getWindow().setNavigationBarColor(backgroundColor);
        getWindow().getDecorView().setSystemUiVisibility(isLightColor(backgroundColor)
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(themeBackground());
        root.setPadding(dp(18), dp(8), dp(18), dp(8));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("LAST FREQUENCY", 21, textColor, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));
        ImageButton theme = new ImageButton(this);
        theme.setBackground(roundRect(accentColor, dp(17), 0));
        theme.setImageDrawable(new ThemeIconDrawable(getResources().getDisplayMetrics().density, textOnAccent()));
        theme.setScaleType(ImageView.ScaleType.CENTER);
        theme.setMinimumWidth(0); theme.setMinimumHeight(0);
        theme.setPadding(dp(11), dp(11), dp(11), dp(11));
        theme.setContentDescription("Сменить тему");
        theme.setOnClickListener(v -> showThemeDialog());
        header.addView(theme, new LinearLayout.LayoutParams(dp(48), dp(46)));
        root.addView(header);

        mainBodyScroll = new ScrollView(this);
        mainBodyScroll.setFillViewport(false);
        mainBodyScroll.setVerticalScrollBarEnabled(false);
        mainBodyContent = new LinearLayout(this);
        mainBodyContent.setOrientation(LinearLayout.VERTICAL);
        mainBodyScroll.addView(mainBodyContent, new ScrollView.LayoutParams(-1, -2));
        root.addView(mainBodyScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextSize(15);
        searchField.setTextColor(textColor);
        searchField.setHintTextColor(mutedColor);
        searchField.setHint("Поиск по трекам и исполнителям");
        searchField.setCompoundDrawablePadding(dp(9));
        searchField.setPadding(dp(15), 0, dp(15), 0);
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchField.setBackground(roundRect(surfaceColor, dp(16), 0));
        mainBodyContent.addView(searchField, new LinearLayout.LayoutParams(-1, dp(48)));
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                if (page == 0) renderPage();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        topTabs = new LinearLayout(this);
        topTabs.setOrientation(LinearLayout.HORIZONTAL);
        topTabs.setPadding(0, dp(13), 0, dp(11));
        tabTracks = tab("Треки"); tabOnline = tab("Поиск"); tabPlaylists = tab("Плейлисты"); tabSettings = tab("Настройки");
        topTabs.addView(tabTracks, new LinearLayout.LayoutParams(0, dp(42), 1f));
        topTabs.addView(tabOnline, new LinearLayout.LayoutParams(0, dp(42), 1f));
        topTabs.addView(tabPlaylists, new LinearLayout.LayoutParams(0, dp(42), 1f));
        topTabs.addView(tabSettings, new LinearLayout.LayoutParams(0, dp(42), 1f));
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.addView(topTabs, new HorizontalScrollView.LayoutParams(-1, -2));
        mainTabScroll = tabScroll;
        mainBodyContent.addView(tabScroll);
        tabTracks.setOnClickListener(v -> { page = 0; selectedPlaylist = ""; renderPage(); });
        tabOnline.setOnClickListener(v -> { page = 1; renderPage(); });
        tabPlaylists.setOnClickListener(v -> { page = 2; renderPage(); });
        tabSettings.setOnClickListener(v -> { page = 3; renderPage(); });

        pageHost = new FrameLayout(this);
        mainBodyContent.addView(pageHost, new LinearLayout.LayoutParams(-1, dp(400)));
        mainBodyScroll.post(this::updateMainBodyViewport);

        playerDock = new LinearLayout(this);
        playerDock.setOrientation(LinearLayout.VERTICAL);
        playerDock.setGravity(Gravity.CENTER_VERTICAL);
        playerDock.setPadding(dp(10), dp(8), dp(8), dp(8));
        playerDock.setBackground(roundRect(cardColor, dp(18), 0));
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(-1, dp(108));
        dockParams.topMargin = dp(10);
        root.addView(playerDock, dockParams);
    }

    private Button tab(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(10.5f);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setTextColor(mutedColor);
        button.setBackground(roundRect(Color.TRANSPARENT, dp(13), 0));
        return button;
    }

    private void renderPage() {
        if (pageHost == null) return;
        pageHost.removeAllViews();
        tabTracks.setTextColor(page == 0 ? textColor : mutedColor);
        tabOnline.setTextColor(page == 1 ? textColor : mutedColor);
        tabPlaylists.setTextColor(page == 2 ? textColor : mutedColor);
        tabSettings.setTextColor(page == 3 ? textColor : mutedColor);
        tabTracks.setBackground(roundRect(page == 0 ? surfaceColor : Color.TRANSPARENT, dp(13), 0));
        tabOnline.setBackground(roundRect(page == 1 ? surfaceColor : Color.TRANSPARENT, dp(13), 0));
        tabPlaylists.setBackground(roundRect(page == 2 ? surfaceColor : Color.TRANSPARENT, dp(13), 0));
        tabSettings.setBackground(roundRect(page == 3 ? surfaceColor : Color.TRANSPARENT, dp(13), 0));
        searchField.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        topTabs.setPadding(0, dp(13), 0, dp(11));
        if (mainBodyScroll != null) mainBodyScroll.post(this::updateMainBodyViewport);
        if (page == 0) renderTracksPage();
        else if (page == 1) renderOnlinePageV2();
        else if (page == 2) renderPlaylistsPage();
        else renderSettingsPage();
    }

    private void updateMainBodyViewport() {
        if (mainBodyScroll == null || pageHost == null || searchField == null || mainTabScroll == null) return;
        int available = Math.max(dp(220), mainBodyScroll.getHeight() - searchField.getMeasuredHeight() - mainTabScroll.getMeasuredHeight());
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) pageHost.getLayoutParams();
        if (params.height != available) {
            params.height = available;
            pageHost.setLayoutParams(params);
        }
    }

    private void renderTracksPage() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button add = pill("＋ Добавить", accentColor, textOnAccent());
        add.setOnClickListener(v -> chooseFiles());
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button folder = pill("Папка", surfaceColor, textColor);
        LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(0, dp(42), 0.7f);
        folderParams.leftMargin = dp(8);
        actions.addView(folder, folderParams);
        folder.setOnClickListener(v -> chooseFolder());
        Button sort = pill(sortLabel(), surfaceColor, textColor);
        LinearLayout.LayoutParams sortParams = new LinearLayout.LayoutParams(0, dp(42), 0.8f);
        sortParams.leftMargin = dp(8);
        actions.addView(sort, sortParams);
        sort.setOnClickListener(v -> showSortMenu(sort));
        content.addView(actions);

        if (!selectedPlaylist.isEmpty()) {
            LinearLayout filter = new LinearLayout(this);
            filter.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = label("Плейлист · " + selectedPlaylist, 13, accentColor, true);
            filter.addView(name, new LinearLayout.LayoutParams(0, dp(40), 1));
            Button clear = pill("Сбросить", surfaceColor, textColor);
            filter.addView(clear, new LinearLayout.LayoutParams(-2, dp(34)));
            clear.setOnClickListener(v -> { selectedPlaylist = ""; renderPage(); });
            content.addView(filter);
        }

        ArrayList<Track> visible = visibleTracks();
        if (visible.isEmpty()) {
            TextView empty = label(tracks.isEmpty() ? "Добавь музыку с телефона — и она появится здесь." : "Ничего не найдено. Попробуй изменить поиск.",
                    15, mutedColor, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(24), dp(30), dp(24), dp(30));
            content.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            ListView list = new ListView(this);
            list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            list.setDividerHeight(dp(7));
            list.setCacheColorHint(Color.TRANSPARENT);
            list.setSelector(android.R.color.transparent);
            list.setAdapter(new TrackAdapter(visible));
            list.setOnItemClickListener((parent, view, position, id) -> playTrack(visible, position));
            list.setOnItemLongClickListener((parent, view, position, id) -> { showTrackPlaylistDialog(visible.get(position)); return true; });
            content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        pageHost.addView(content, new FrameLayout.LayoutParams(-1, -1));
    }

    private void renderOnlinePage() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView title = sectionTitle("ПОИСК МУЗЫКИ И ЗАГРУЗКА MP3");
        content.addView(title);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        onlineSearchField = new EditText(this);
        onlineSearchField.setSingleLine(true);
        onlineSearchField.setTextSize(14);
        onlineSearchField.setTextColor(textColor);
        onlineSearchField.setHintTextColor(mutedColor);
        onlineSearchField.setHint("Трек, исполнитель или ссылка");
        onlineSearchField.setInputType(InputType.TYPE_CLASS_TEXT);
        onlineSearchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        onlineSearchField.setPadding(dp(13), 0, dp(13), 0);
        onlineSearchField.setBackground(roundRect(surfaceColor, dp(15), 0));
        onlineSearchField.setText(onlineQuery);
        searchRow.addView(onlineSearchField, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button go = pill("Искать", accentColor, textOnAccent());
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(-2, dp(48));
        goParams.leftMargin = dp(7);
        searchRow.addView(go, goParams);
        content.addView(searchRow);
        onlineSearchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { onlineQuery = s.toString(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        onlineSearchField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { searchOnline(); return true; }
            return false;
        });

        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        TextView source = label("Источник: " + providerLabel(onlineProvider), 12, mutedColor, false);
        source.setPadding(dp(4), 0, 0, 0);
        filters.addView(source, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button choose = pill("Изменить", surfaceColor, textColor);
        choose.setOnClickListener(v -> chooseOnlineProvider());
        filters.addView(choose, new LinearLayout.LayoutParams(-2, dp(34)));
        content.addView(filters);
        go.setOnClickListener(v -> searchOnline());

        LinearLayout outsideSearch = new LinearLayout(this);
        outsideSearch.setGravity(Gravity.CENTER_VERTICAL);
        Button spotify = pill("Spotify", surfaceColor, textColor);
        Button yandex = pill("Яндекс Музыка", surfaceColor, textColor);
        LinearLayout.LayoutParams outsideParams = new LinearLayout.LayoutParams(0, dp(36), 1);
        outsideParams.rightMargin = dp(6);
        outsideSearch.addView(spotify, outsideParams);
        outsideSearch.addView(yandex, new LinearLayout.LayoutParams(0, dp(36), 1));
        spotify.setOnClickListener(v -> openExternalSearch("https://open.spotify.com/search/"));
        yandex.setOnClickListener(v -> openExternalSearch("https://music.yandex.ru/search?text="));
        content.addView(outsideSearch);

        String initialStatus = onlineBusy ? "Ищу музыку…" : onlineEngineReady
                ? "Поиск по YouTube Music, YouTube и SoundCloud. Нажми на трек для воспроизведения, на ↓ — для MP3."
                : onlineEngineError.isEmpty() ? "Запускаю онлайн-поиск…" : onlineEngineError;
        onlineStatus = label(initialStatus,
                12, mutedColor, false);
        onlineStatus.setPadding(dp(4), dp(5), dp(4), dp(8));
        content.addView(onlineStatus);

        if (onlineResults.isEmpty()) {
            TextView empty = label(onlineBusy ? "Подключаюсь к музыкальным источникам…" : "Результаты поиска появятся здесь.",
                    14, mutedColor, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(24), dp(18), dp(24));
            content.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            onlineList = new ListView(this);
            onlineList.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            onlineList.setDividerHeight(dp(7));
            onlineList.setCacheColorHint(Color.TRANSPARENT);
            onlineList.setSelector(android.R.color.transparent);
            onlineList.setAdapter(new OnlineAdapter());
            onlineList.setOnItemClickListener((parent, view, position, id) -> {
                Track track = onlineResults.get(position);
                if (track.isPlaylist) openOnlinePlaylist(track); else playOnline(track);
            });
            onlineList.setOnItemLongClickListener((parent, view, position, id) -> {
                onlineTrackMenu(onlineResults.get(position)); return true;
            });
            content.addView(onlineList, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        pageHost.addView(content, new FrameLayout.LayoutParams(-1, -1));
    }

    private void renderOnlinePageV2() {
        if (onlineQuery.trim().isEmpty() && currentOnlinePlaylistTitle.isEmpty() && !onlineSearchBusy) {
            showingRecentlyPlayed = !recentlyPlayed.isEmpty();
            onlineResults.clear(); onlineResults.addAll(recentlyPlayed);
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, 0);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        onlineSearchField = new EditText(this);
        onlineSearchField.setSingleLine(true);
        onlineSearchField.setTextSize(15);
        onlineSearchField.setTextColor(textColor);
        onlineSearchField.setHintTextColor(mutedColor);
        onlineSearchField.setHint("Название трека или ссылка");
        onlineSearchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        onlineSearchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        onlineSearchField.setPadding(dp(14), 0, dp(14), 0);
        onlineSearchField.setBackground(roundRect(surfaceColor, dp(16), 0));
        onlineSearchField.setText(onlineQuery);
        searchRow.addView(onlineSearchField, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button options = pill("⋮", surfaceColor, textColor);
        options.setTextSize(24);
        options.setMinWidth(0); options.setMinimumWidth(0); options.setPadding(0, 0, 0, dp(3));
        options.setContentDescription("Параметры поиска");
        LinearLayout.LayoutParams optionsParams = new LinearLayout.LayoutParams(dp(48), dp(50));
        optionsParams.leftMargin = dp(7);
        searchRow.addView(options, optionsParams);
        content.addView(searchRow, new LinearLayout.LayoutParams(-1, dp(54)));
        options.setOnClickListener(v -> showOnlineSearchOptions());

        LinearLayout resultHeading = new LinearLayout(this);
        resultHeading.setGravity(Gravity.CENTER_VERTICAL);
        resultHeading.setPadding(dp(2), dp(9), 0, dp(5));
        onlineResultTitle = label(currentOnlinePlaylistTitle.isEmpty()
                ? (showingRecentlyPlayed ? "Прослушать повторно" : onlineQuery.isEmpty() ? "Подсказки по трекам" : "Результаты поиска")
                : currentOnlinePlaylistTitle, 13, textColor, true);
        resultHeading.addView(onlineResultTitle, new LinearLayout.LayoutParams(0, dp(36), 1));
        onlineBack = pill("← Назад", surfaceColor, textColor);
        onlineBack.setTextSize(11);
        onlineBack.setVisibility(currentOnlinePlaylistTitle.isEmpty() ? View.GONE : View.VISIBLE);
        onlineBack.setOnClickListener(v -> closeOnlinePlaylist());
        resultHeading.addView(onlineBack, new LinearLayout.LayoutParams(-2, dp(34)));
        content.addView(resultHeading);

        onlineStatus = label(onlineSearchBusy ? "Ищу по выбранным источникам…"
                : showingRecentlyPlayed ? "Недавно прослушанные треки"
                : onlineEngineReady ? "Поиск начинается после ввода запроса. Можно вставить ссылку на трек или плейлист."
                : onlineEngineError.isEmpty() ? "Подключаю музыкальные источники…" : onlineEngineError,
                11, mutedColor, false);
        onlineStatus.setPadding(dp(3), 0, dp(3), dp(5));
        content.addView(onlineStatus);

        onlineEmpty = label("Введи название трека или вставь ссылку, чтобы начать поиск.", 14, mutedColor, false);
        onlineEmpty.setGravity(Gravity.CENTER);
        onlineEmpty.setPadding(dp(20), dp(26), dp(20), dp(26));
        content.addView(onlineEmpty, new LinearLayout.LayoutParams(-1, 0, 1));
        onlineList = new ListView(this);
        onlineList.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        onlineList.setDividerHeight(dp(7));
        onlineList.setCacheColorHint(Color.TRANSPARENT);
        onlineList.setSelector(android.R.color.transparent);
        onlineList.setAdapter(new OnlineAdapter());
        onlineList.setEmptyView(onlineEmpty);
        onlineList.setOnItemClickListener((parent, view, position, id) -> {
            Track track = onlineResults.get(position);
            if (track.isPlaylist) openOnlinePlaylist(track);
            else if (showingRecentlyPlayed) playRecentlyPlayed(track);
            else playOnline(track);
        });
        onlineList.setOnItemLongClickListener((parent, view, position, id) -> {
            onlineTrackMenu(onlineResults.get(position)); return true;
        });
        content.addView(onlineList, new LinearLayout.LayoutParams(-1, 0, 1));
        pageHost.addView(content, new FrameLayout.LayoutParams(-1, -1));
        updateOnlineSearchViews();

        onlineSearchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                onlineQuery = s.toString();
                if (!currentOnlinePlaylistTitle.isEmpty()) currentOnlinePlaylistTitle = "";
                onlineSearchSequence++;
                onlineSearchBusy = false;
                if (pendingOnlineSearch != null) searchHandler.removeCallbacks(pendingOnlineSearch);
                if (onlineQuery.trim().isEmpty()) {
                    showingRecentlyPlayed = !recentlyPlayed.isEmpty();
                    onlineResults.clear(); onlineResults.addAll(recentlyPlayed);
                    onlineStatus.setText(showingRecentlyPlayed ? "Недавно прослушанные треки"
                            : "Поиск начинается после ввода запроса. Можно вставить ссылку на трек или плейлист.");
                    updateOnlineSearchViews();
                } else {
                    showingRecentlyPlayed = false;
                    onlineResults.clear();
                    onlineStatus.setText("Подбираю результаты…");
                    updateOnlineSearchViews();
                    String term = onlineQuery.trim();
                    long delay = term.length() < 2 || term.startsWith("https://") || term.startsWith("http://") ? 0 : 450;
                    scheduleOnlineSearch(delay);
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        onlineSearchField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideSearchKeyboard(); searchOnline(true); return true;
            }
            return false;
        });
    }

    private void showOnlineSearchOptions() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));
        TextView sourceTitle = label("Где искать", 13, mutedColor, true);
        form.addView(sourceTitle, new LinearLayout.LayoutParams(-1, dp(34)));
        CheckBox[] sourceChecks = new CheckBox[ONLINE_SOURCE_KEYS.length];
        for (int i = 0; i < ONLINE_SOURCE_KEYS.length; i++) {
            CheckBox check = new CheckBox(this);
            check.setText(ONLINE_SOURCE_NAMES[i]);
            check.setTextSize(14);
            check.setTextColor(textColor);
            check.setButtonTintList(android.content.res.ColorStateList.valueOf(accentColor));
            check.setChecked(selectedOnlineProviders.contains(ONLINE_SOURCE_KEYS[i]));
            sourceChecks[i] = check;
            form.addView(check, new LinearLayout.LayoutParams(-1, dp(42)));
        }
        TextView kindTitle = label("Искать", 13, mutedColor, true);
        LinearLayout.LayoutParams kindTitleParams = new LinearLayout.LayoutParams(-1, dp(38));
        kindTitleParams.topMargin = dp(6);
        form.addView(kindTitle, kindTitleParams);
        RadioGroup kindGroup = new RadioGroup(this);
        kindGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton tracksChoice = new RadioButton(this);
        tracksChoice.setText("Треки"); tracksChoice.setTextColor(textColor);
        tracksChoice.setButtonTintList(android.content.res.ColorStateList.valueOf(accentColor));
        tracksChoice.setId(View.generateViewId());
        RadioButton playlistsChoice = new RadioButton(this);
        playlistsChoice.setText("Плейлисты"); playlistsChoice.setTextColor(textColor);
        playlistsChoice.setButtonTintList(android.content.res.ColorStateList.valueOf(accentColor));
        playlistsChoice.setId(View.generateViewId());
        kindGroup.addView(tracksChoice, new RadioGroup.LayoutParams(0, dp(48), 1f));
        kindGroup.addView(playlistsChoice, new RadioGroup.LayoutParams(0, dp(48), 1f));
        kindGroup.check("playlist".equals(onlineKind) ? playlistsChoice.getId() : tracksChoice.getId());
        form.addView(kindGroup);

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Параметры поиска").setView(form)
                .setNegativeButton("Отмена", null).setPositiveButton("Применить", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            LinkedHashSet<String> nextSources = new LinkedHashSet<>();
            for (int i = 0; i < sourceChecks.length; i++) if (sourceChecks[i].isChecked()) nextSources.add(ONLINE_SOURCE_KEYS[i]);
            if (nextSources.isEmpty()) {
                Toast.makeText(this, "Выбери хотя бы один источник", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedOnlineProviders.clear(); selectedOnlineProviders.addAll(nextSources);
            onlineKind = kindGroup.getCheckedRadioButtonId() == playlistsChoice.getId() ? "playlist" : "track";
            String saved = android.text.TextUtils.join(",", selectedOnlineProviders);
            onlineProvider = selectedOnlineProviders.size() == ONLINE_SOURCE_KEYS.length ? "all" : saved;
            preferences.edit().putString(KEY_ONLINE_SOURCES, saved).putString("online_kind", onlineKind)
                    .putString("online_provider", onlineProvider).apply();
            currentOnlinePlaylistTitle = "";
            onlineSearchSequence++;
            onlineSearchBusy = false;
            if (onlineQuery.trim().isEmpty()) renderPage();
            else searchOnline(true);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void scheduleOnlineSearch(long delayMs) {
        if (pendingOnlineSearch != null) searchHandler.removeCallbacks(pendingOnlineSearch);
        long expectedSequence = onlineSearchSequence;
        pendingOnlineSearch = () -> {
            if (expectedSequence == onlineSearchSequence) searchOnline(false);
        };
        searchHandler.postDelayed(pendingOnlineSearch, delayMs);
    }

    private void hideSearchKeyboard() {
        if (onlineSearchField == null) return;
        android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(onlineSearchField.getWindowToken(), 0);
    }

    private void setOnlineKind(String kind) {
        if (kind.equals(onlineKind)) return;
        onlineKind = kind;
        preferences.edit().putString("online_kind", kind).apply();
        currentOnlinePlaylistTitle = "";
        onlineSearchSequence++;
        onlineSearchBusy = false;
        refreshOnlineSearchFilters();
        if (!onlineQuery.trim().isEmpty()) scheduleOnlineSearch("playlist".equals(kind) ? 100 : 180);
    }

    private void setOnlineProvider(String provider) {
        if (provider.equals(onlineProvider)) return;
        onlineProvider = provider;
        preferences.edit().putString("online_provider", provider).apply();
        currentOnlinePlaylistTitle = "";
        onlineSearchSequence++;
        onlineSearchBusy = false;
        refreshOnlineSearchFilters();
        if (!onlineQuery.trim().isEmpty()) scheduleOnlineSearch(180);
    }

    private void refreshOnlineSearchFilters() {
        if (onlineKindTracks != null) {
            boolean tracksSelected = "track".equals(onlineKind);
            onlineKindTracks.setBackground(roundRect(tracksSelected ? accentColor : surfaceColor, dp(13), 0));
            onlineKindTracks.setTextColor(tracksSelected ? textOnAccent() : mutedColor);
            onlineKindPlaylists.setBackground(roundRect(!tracksSelected ? accentColor : surfaceColor, dp(13), 0));
            onlineKindPlaylists.setTextColor(!tracksSelected ? textOnAccent() : mutedColor);
        }
        String[] keys = {"all", "youtube_music", "youtube", "soundcloud", "deezer", "apple"};
        if (onlineProviderButtons != null) for (int i = 0; i < onlineProviderButtons.length; i++) {
            boolean selected = keys[i].equals(onlineProvider);
            onlineProviderButtons[i].setBackground(roundRect(selected ? accentColor : surfaceColor, dp(12), 0));
            onlineProviderButtons[i].setTextColor(selected ? textOnAccent() : textColor);
        }
    }

    private void updateOnlineSearchViews() {
        if (onlineResultTitle != null) onlineResultTitle.setText(currentOnlinePlaylistTitle.isEmpty()
                ? (showingRecentlyPlayed ? "Прослушать повторно" : onlineQuery.isEmpty() ? "Подсказки по трекам" : "Результаты поиска") : currentOnlinePlaylistTitle);
        if (onlineBack != null) onlineBack.setVisibility(currentOnlinePlaylistTitle.isEmpty() ? View.GONE : View.VISIBLE);
        if (onlineEmpty != null) {
            String empty = onlineSearchBusy ? "Ищу музыку на выбранных площадках…"
                    : onlineQuery.trim().length() == 1 && !onlineQuery.startsWith("http")
                    ? "Введи ещё один символ, чтобы запустить поиск."
                    : onlineQuery.trim().isEmpty() ? (recentlyPlayed.isEmpty()
                        ? "Введи название трека или вставь ссылку, чтобы начать поиск."
                        : "Здесь появятся недавно прослушанные треки.")
                    : "Ничего не найдено. Попробуй другое название или источник.";
            onlineEmpty.setText(empty);
        }
        if (onlineList != null && onlineList.getAdapter() != null) {
            ((BaseAdapter) onlineList.getAdapter()).notifyDataSetChanged();
        }
    }

    private void searchOnline(boolean submitted) {
        String term = onlineQuery.trim();
        if (pendingOnlineSearch != null) searchHandler.removeCallbacks(pendingOnlineSearch);
        if (term.isEmpty()) return;
        boolean isUrl = term.startsWith("https://") || term.startsWith("http://");
        if (term.length() < 2 && !isUrl) {
            onlineSearchBusy = false;
            onlineStatus.setText("Введи ещё один символ, чтобы запустить поиск.");
            updateOnlineSearchViews();
            return;
        }
        if (!onlineEngineReady) {
            onlineStatus.setText(onlineEngineError.isEmpty() ? "Подключаю музыкальные источники…" : onlineEngineError);
            return;
        }
        long requestId = ++onlineSearchSequence;
        String selectedSources = selectedOnlineSourceArgument();
        onlineSearchBusy = true;
        showingRecentlyPlayed = false;
        currentOnlinePlaylistTitle = "";
        previousOnlineResults.clear();
        onlineResults.clear();
        onlineStatus.setText("Ищу " + ("playlist".equals(onlineKind) ? "плейлисты" : "треки") + " · " + providerLabel(selectedSources) + "…");
        updateOnlineSearchViews();
        onlineSearchWorker.execute(() -> {
            OnlineCatalog.SearchResponse loadedResponse;
            String failure = "";
            try { loadedResponse = OnlineCatalog.search(term, selectedSources, onlineKind); }
            catch (Exception error) {
                loadedResponse = new OnlineCatalog.SearchResponse();
                failure = error.getMessage() == null ? "Поиск временно недоступен" : error.getMessage();
            }
            final OnlineCatalog.SearchResponse response = loadedResponse;
            ArrayList<Track> found = new ArrayList<>();
            for (JSONObject item : response.items) {
                String uri = item.optString("url", "");
                if (uri.isEmpty()) continue;
                Track track = new Track(uri, item.optString("title", "Без названия"),
                        item.optString("artist", "Неизвестный автор"), item.optString("album", ""),
                        item.optString("provider", ""), item.optString("thumbnail", ""));
                track.previewOnly = item.optBoolean("preview_only", false);
                track.previewUrl = item.optString("preview_url", "");
                track.isPlaylist = item.optBoolean("is_playlist", false) || "playlist".equals(item.optString("kind", ""));
                track.trackCount = item.optInt("track_count", 0);
                track.duration = item.optInt("duration", 0);
                track.orderNumber = item.optInt("track_number", 0);
                found.add(track);
            }
            String error = failure;
            if (error.isEmpty() && found.isEmpty() && !response.errors.isEmpty()) error = android.text.TextUtils.join(" · ", response.errors);
            String finalError = error;
            runOnUiThread(() -> {
                if (requestId != onlineSearchSequence || page != 1) return;
                onlineSearchBusy = false;
                onlineResults.clear(); onlineResults.addAll(found);
                previousOnlineResults.clear(); previousOnlineResults.addAll(found);
                String message = !finalError.isEmpty() ? finalError
                        : found.isEmpty() ? (response.errors.isEmpty() ? "Ничего не найдено. Попробуй другое название или источник."
                            : android.text.TextUtils.join(" · ", response.errors))
                        : "Найдено вариантов: " + found.size() + (response.errors.isEmpty() ? "" : " · " + android.text.TextUtils.join(" · ", response.errors));
                if (onlineStatus != null) onlineStatus.setText(message);
                updateOnlineSearchViews();
            });
        });
    }

    private void openOnlinePlaylist(Track playlist) {
        if (!playlist.isPlaylist || onlineSearchBusy) return;
        long requestId = ++onlineSearchSequence;
        onlineSearchBusy = true;
        previousOnlineResults.clear(); previousOnlineResults.addAll(onlineResults);
        currentOnlinePlaylistTitle = playlist.title;
        onlineResults.clear();
        if (onlineStatus != null) onlineStatus.setText("Загружаю треки плейлиста…");
        updateOnlineSearchViews();
        onlineSearchWorker.execute(() -> {
            ArrayList<Track> found = new ArrayList<>();
            String title = playlist.title, error = "";
            try {
                JSONObject data = OnlineCatalog.loadPlaylist(playlist.uri);
                title = data.optString("title", title);
                JSONArray entries = data.optJSONArray("tracks");
                if (entries != null) for (int i = 0; i < entries.length(); i++) {
                    JSONObject item = entries.optJSONObject(i);
                    if (item == null) continue;
                    Track track = new Track(item.optString("url", ""), item.optString("title", "Без названия"),
                            item.optString("artist", "Неизвестный автор"), OnlineCatalog.providerName(item.optString("provider", "")),
                            item.optString("provider", ""), item.optString("thumbnail", ""));
                    if (!track.uri.isEmpty()) found.add(track);
                }
            } catch (Exception exception) { error = exception.getMessage() == null ? "Не удалось загрузить плейлист" : exception.getMessage(); }
            String finalTitle = title, finalError = error;
            runOnUiThread(() -> {
                if (requestId != onlineSearchSequence || page != 1) return;
                onlineSearchBusy = false;
                currentOnlinePlaylistTitle = finalTitle;
                onlineResults.clear(); onlineResults.addAll(found);
                if (onlineStatus != null) onlineStatus.setText(!finalError.isEmpty() ? finalError : found.size() + " треков в плейлисте");
                updateOnlineSearchViews();
            });
        });
    }

    private void closeOnlinePlaylist() {
        onlineSearchSequence++;
        onlineSearchBusy = false;
        currentOnlinePlaylistTitle = "";
        onlineResults.clear(); onlineResults.addAll(previousOnlineResults);
        previousOnlineResults.clear();
        if (onlineStatus != null) onlineStatus.setText(onlineResults.size() + " вариантов");
        updateOnlineSearchViews();
    }

    private final class OnlineAdapter extends BaseAdapter {
        @Override public int getCount() { return onlineResults.size(); }
        @Override public Object getItem(int position) { return onlineResults.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Track track = onlineResults.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(8), dp(8));
            row.setBackground(roundRect(cardColor, dp(15), 0));
            ImageView cover = new ImageView(MainActivity.this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(roundRect(surfaceColor, dp(10), 0));
            cover.setImageResource(android.R.drawable.ic_media_play);
            row.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(48)));
            if (track.online) loadRemoteArtwork(track, cover);
            else {
                Bitmap localArt = artwork(track);
                if (localArt != null) cover.setImageBitmap(localArt);
            }

            LinearLayout info = new LinearLayout(MainActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.CENTER_VERTICAL);
            info.setPadding(dp(10), 0, dp(5), 0);
            TextView name = label(displayTrackTitle(track), 13, textColor, true);
            name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView artist = label(track.artist, 11, mutedColor, false);
            artist.setSingleLine(true); artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
            String metadata = showingRecentlyPlayed && !track.online ? "Локальная библиотека"
                    : track.isPlaylist
                    ? (track.trackCount > 0 ? track.trackCount + " треков · " : "") + providerLabel(track.provider)
                    : providerLabel(track.provider) + (track.album.isEmpty() ? "" : " · " + track.album)
                    + (track.duration > 0 ? " · " + formatDuration(track.duration) : "");
            TextView source = label(metadata, 9, mutedColor, false);
            source.setSingleLine(true); source.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(name); info.addView(artist); info.addView(source);
            row.addView(info, new LinearLayout.LayoutParams(0, dp(56), 1));
            Button download = pill(showingRecentlyPlayed ? "Играть" : track.isPlaylist ? "Открыть" : track.previewOnly ? "30с" : "↓ MP3",
                    accentColor, textOnAccent());
            download.setTextSize(10);
            download.setContentDescription(showingRecentlyPlayed ? "Снова включить: " + track.title
                    : track.isPlaylist ? "Открыть плейлист: " + track.title
                    : track.previewOnly ? "Прослушать превью: " + track.title : "Скачать MP3: " + track.title);
            if (showingRecentlyPlayed) download.setOnClickListener(v -> playRecentlyPlayed(track));
            else if (track.isPlaylist) download.setOnClickListener(v -> openOnlinePlaylist(track));
            else if (track.previewOnly) download.setOnClickListener(v -> playOnline(track));
            else download.setOnClickListener(v -> downloadOnline(track));
            row.addView(download, new LinearLayout.LayoutParams(-2, dp(40)));
            if (!showingRecentlyPlayed && !track.isPlaylist) {
                Button more = pill("⋮", surfaceColor, textColor);
                more.setTextSize(19);
                more.setPadding(0, 0, 0, dp(2));
                more.setContentDescription("Действия с треком");
                LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(34), dp(40));
                moreParams.leftMargin = dp(4);
                row.addView(more, moreParams);
                more.setOnClickListener(v -> onlineTrackMenu(track));
            }
            return row;
        }
    }

    private void playRecentlyPlayed(Track track) {
        Track local = findTrack(track.uri);
        if (local != null) {
            ArrayList<Track> rows = visibleTracks();
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).uri.equals(local.uri)) { playTrack(rows, i); return; }
            }
        }
        if (track.online) { playOnline(track); return; }
        try {
            JSONArray queue = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("uri", track.uri); item.put("title", track.title); item.put("artist", track.artist); item.put("album", track.album);
            queue.put(item); startQueue(queue, 0);
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось снова включить этот трек", Toast.LENGTH_SHORT).show();
        }
    }

    private String providerLabel(String provider) {
        if ("all".equals(provider)) return "Все";
        if (provider != null && provider.contains(",")) {
            int count = provider.split(",").length;
            return count + " источника";
        }
        return OnlineCatalog.providerName(provider);
    }

    private String selectedOnlineSourceArgument() {
        if (selectedOnlineProviders.size() == ONLINE_SOURCE_KEYS.length) return "all";
        return android.text.TextUtils.join(",", selectedOnlineProviders);
    }

    private static boolean isOnlineSource(String provider) {
        if (provider == null) return false;
        for (String key : ONLINE_SOURCE_KEYS) if (key.equals(provider)) return true;
        return false;
    }

    private String formatDuration(int seconds) {
        if (seconds <= 0) return "";
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void chooseOnlineProvider() {
        String[] labels = {"Все источники", "YouTube Music", "YouTube", "SoundCloud"};
        String[] keys = {"all", "youtube_music", "youtube", "soundcloud"};
        int selected = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(onlineProvider)) selected = i;
        new AlertDialog.Builder(this).setTitle("Источник поиска")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    onlineProvider = keys[which]; dialog.dismiss(); renderPage();
                }).setNegativeButton("Отмена", null).show();
    }

    private void searchOnline() {
        String term = onlineQuery.trim();
        if (term.isEmpty()) { Toast.makeText(this, "Введи название трека или исполнителя", Toast.LENGTH_SHORT).show(); return; }
        if (onlineBusy) return;
        onlineBusy = true;
        if (onlineStatus != null) onlineStatus.setText("Ищу музыку…");
        Toast.makeText(this, "Поиск запущен", Toast.LENGTH_SHORT).show();
        importer.execute(() -> {
            ArrayList<Track> found = new ArrayList<>();
            String failure = "";
            try {
                for (JSONObject item : OnlineCatalog.search(term, onlineProvider)) {
                    String uri = item.optString("url", "");
                    if (uri.isEmpty()) continue;
                    found.add(new Track(uri, item.optString("title", "Без названия"),
                            item.optString("artist", "Неизвестный автор"),
                            OnlineCatalog.providerName(item.optString("provider", "")),
                            item.optString("provider", ""), item.optString("thumbnail", "")));
                    Track added = found.get(found.size() - 1);
                    added.previewOnly = item.optBoolean("preview_only", false);
                    added.previewUrl = item.optString("preview_url", "");
                }
            } catch (Exception error) {
                failure = error.getMessage() == null ? "Поиск временно недоступен" : error.getMessage();
            }
            String message = failure;
            runOnUiThread(() -> {
                onlineBusy = false;
                onlineResults.clear(); onlineResults.addAll(found);
                if (page == 1) {
                    renderPage();
                    if (onlineStatus != null) onlineStatus.setText(!message.isEmpty() ? message
                            : found.isEmpty() ? "Ничего не найдено. Проверь запрос или выбери другой источник."
                            : "Найдено треков: " + found.size() + " · нажми на трек для воспроизведения или ↓ MP3 для загрузки.");
                }
            });
        });
    }

    private void onlineTrackMenu(Track track) {
        if (track.isPlaylist) {
            new AlertDialog.Builder(this).setTitle(track.title)
                    .setItems(new String[]{"Открыть плейлист"}, (dialog, which) -> openOnlinePlaylist(track))
                    .setNegativeButton("Закрыть", null).show();
            return;
        }
        String[] actions = track.previewOnly
                ? new String[]{"Прослушать 30-секундное превью", "Добавить в плейлист", "Добавить в библиотеку", "Открыть источник"}
                : new String[]{"Воспроизвести", "Добавить в плейлист", "Скачать MP3", "Добавить в библиотеку", "Открыть источник"};
        new AlertDialog.Builder(this).setTitle(track.title).setItems(actions, (dialog, which) -> {
            try {
                if (which == 0) playOnline(track);
                else if (which == 1) showTrackPlaylistDialog(track);
                else if (track.previewOnly && which == 2 || !track.previewOnly && which == 3) addOnlineToLibrary(track);
                else if (!track.previewOnly && which == 2) downloadOnline(track);
                else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(track.uri)));
            } catch (Exception ignored) { Toast.makeText(this, "Не удалось открыть источник", Toast.LENGTH_SHORT).show(); }
        }).show();
    }

    private void openExternalSearch(String baseUrl) {
        String term = onlineQuery.trim();
        if (term.isEmpty()) { Toast.makeText(this, "Сначала введи название трека", Toast.LENGTH_SHORT).show(); return; }
        try {
            String encoded = java.net.URLEncoder.encode(term, "UTF-8");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + encoded)));
        } catch (Exception error) { Toast.makeText(this, "Не удалось открыть поиск", Toast.LENGTH_SHORT).show(); }
    }

    private void addOnlineToLibrary(Track track) {
        if (findTrack(track.uri) != null) { Toast.makeText(this, "Трек уже в библиотеке", Toast.LENGTH_SHORT).show(); return; }
        Track saved = new Track(track.uri, track.title, track.artist, track.album, track.provider, track.artworkUrl);
        saved.previewOnly = track.previewOnly;
        saved.previewUrl = track.previewUrl;
        tracks.add(saved); saveLibrary();
        Toast.makeText(this, "Добавлено в библиотеку", Toast.LENGTH_SHORT).show();
    }

    private void playOnline(Track track) {
        if (onlineBusy) { Toast.makeText(this, "Подожди, выполняется поиск или загрузка", Toast.LENGTH_SHORT).show(); return; }
        onlineBusy = true;
        if (onlineStatus != null) onlineStatus.setText("Подготавливаю поток…");
        importer.execute(() -> {
            try {
                JSONObject stream;
                boolean externalCatalogTrack = "spotify".equals(track.provider) || "apple".equals(track.provider)
                        || "deezer".equals(track.provider) || "yandex".equals(track.provider);
                if (!track.previewOnly || externalCatalogTrack) {
                    try {
                        String pageUrl = getPlayablePageUrl(track);
                        stream = OnlineCatalog.resolve(pageUrl);
                    } catch (Exception matchError) {
                        if (track.previewUrl.isEmpty()) throw matchError;
                        stream = makePreviewStream(track);
                    }
                } else {
                    stream = makePreviewStream(track);
                }
                JSONArray queue = new JSONArray();
                JSONObject item = new JSONObject();
                item.put("uri", stream.optString("url"));
                item.put("source_uri", track.uri);
                item.put("title", stream.optString("title", track.title));
                item.put("artist", stream.optString("artist", track.artist));
                item.put("album", track.album.isEmpty() ? OnlineCatalog.providerName(track.provider) : track.album);
                item.put("cover_url", track.artworkUrl.isEmpty() ? stream.optString("thumbnail", "") : track.artworkUrl);
                item.put("headers", stream.optJSONObject("headers") == null ? new JSONObject() : stream.optJSONObject("headers"));
                queue.put(item);
                runOnUiThread(() -> {
                    onlineBusy = false;
                    startQueue(queue, 0);
                    if (onlineStatus != null) onlineStatus.setText("Сейчас играет: " + track.title);
                });
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Не удалось открыть аудиопоток" : error.getMessage();
                runOnUiThread(() -> {
                    onlineBusy = false;
                    if (onlineStatus != null) onlineStatus.setText(message);
                    Toast.makeText(this, "Не удалось воспроизвести. " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private JSONObject makePreviewStream(Track track) throws Exception {
        if (track.previewUrl.isEmpty()) throw new IllegalStateException("Не удалось найти полный трек или превью для прослушивания.");
        JSONObject stream = new JSONObject();
        stream.put("url", track.previewUrl);
        stream.put("title", track.title);
        stream.put("artist", track.artist);
        stream.put("thumbnail", track.artworkUrl);
        stream.put("headers", new JSONObject());
        return stream;
    }

    private String getPlayablePageUrl(Track track) throws Exception {
        if (track == null || track.uri == null || track.uri.trim().isEmpty()) throw new IllegalArgumentException("Нет ссылки на трек.");
        String provider = track.provider == null ? "" : track.provider.toLowerCase(Locale.ROOT);
        if ("spotify".equals(provider) || "apple".equals(provider) || "yandex".equals(provider) || "deezer".equals(provider)
                || !PlatformCatalog.providerForUrl(track.uri).isEmpty()) {
            JSONObject match = OnlineCatalog.findPlayableMatch(track.title, track.artist, track.duration);
            return match.optString("url", "");
        }
        return track.uri;
    }

    private void startQueue(JSONArray queue, int index) {
        Intent service = new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_SET_QUEUE)
                .putExtra(PlaybackService.EXTRA_QUEUE, queue.toString()).putExtra(PlaybackService.EXTRA_INDEX, index);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service); else startService(service);
        renderPlayerDock();
    }

    private void downloadOnline(Track track) {
        if (onlineBusy) { Toast.makeText(this, "Подожди, выполняется поиск или воспроизведение", Toast.LENGTH_SHORT).show(); return; }
        String[] choices = new String[playlists.size() + 2];
        choices[0] = "Скачать без плейлиста";
        for (int i = 0; i < playlists.size(); i++) choices[i + 1] = playlists.get(i).name;
        choices[choices.length - 1] = "＋ Создать новый плейлист";
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("В какой плейлист сохранить?")
                .setItems(choices, (d, which) -> {
                    String folder = preferences.getString(KEY_DOWNLOAD_FOLDER, "");
                    if (which == 0) downloadOnlineToPlaylist(track, folder, "");
                    else if (which == choices.length - 1) createPlaylistForDownload(track, folder);
                    else downloadOnlineToPlaylist(track, folder, playlists.get(which - 1).name);
                }).setNegativeButton("Отмена", null).create();
        dialog.show();
    }

    private void createPlaylistForDownload(Track track, String folderUri) {
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setHint("Название плейлиста");
        input.setTextColor(textColor); input.setHintTextColor(mutedColor);
        input.setPadding(dp(14), 0, dp(14), 0); input.setBackground(roundRect(surfaceColor, dp(12), 0));
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(22), dp(8), dp(22), 0);
        form.addView(input, new LinearLayout.LayoutParams(-1, dp(48)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Создать плейлист")
                .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Создать и скачать", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) { input.setError("Введи название"); return; }
            if (findPlaylist(name) != null) { input.setError("Такой плейлист уже есть"); return; }
            playlists.add(new Playlist(name));
            savePlaylists();
            dialog.dismiss();
            downloadOnlineToPlaylist(track, folderUri, name);
        }));
        dialog.show();
    }

    private void downloadOnlineToPlaylist(Track track, String folderUri, String playlistName) {
        if (onlineBusy) { Toast.makeText(this, "Подожди, выполняется поиск или воспроизведение", Toast.LENGTH_SHORT).show(); return; }
        onlineBusy = true;
        if (onlineStatus != null) onlineStatus.setText("Загружаю MP3 · " + track.title);
        importer.execute(() -> {
            try {
                String pageUrl = getPlayablePageUrl(track);
                java.io.File file = OnlineCatalog.download(getApplicationContext(), pageUrl, percent -> runOnUiThread(() -> {
                    if (onlineStatus != null) onlineStatus.setText("Загружаю MP3 · " + track.title + " · " + Math.round(percent) + "%");
                }));
                Uri savedUri = publishDownloadedFile(file, folderUri);
                if (savedUri != null) file.delete();
                Track downloaded = readTrack(savedUri == null ? Uri.fromFile(file) : savedUri);
                runOnUiThread(() -> {
                    onlineBusy = false;
                    if (findTrack(downloaded.uri) == null) tracks.add(downloaded);
                    saveLibrary();
                    if (playlistName != null && !playlistName.trim().isEmpty()) {
                        Playlist playlist = findPlaylist(playlistName);
                        if (playlist == null) { playlist = new Playlist(playlistName); playlists.add(playlist); }
                        if (!playlist.ids.contains(downloaded.uri)) playlist.ids.add(downloaded.uri);
                        savePlaylists();
                    }
                    if (onlineStatus != null) onlineStatus.setText("Скачано в библиотеку: " + downloaded.title);
                    Toast.makeText(this, playlistName == null || playlistName.isEmpty()
                            ? "MP3 сохранён" : "MP3 добавлен в «" + playlistName + "»", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Загрузка не удалась" : error.getMessage();
                runOnUiThread(() -> {
                    onlineBusy = false;
                    if (onlineStatus != null) onlineStatus.setText(message);
                    Toast.makeText(this, "Не удалось скачать. " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadRemoteArtwork(Track track, ImageView target) {
        if (track.artworkUrl == null || track.artworkUrl.isEmpty()) return;
        target.setTag(track.uri);
        Bitmap cached = artworkCache.get(track.uri);
        if (cached != null) {
            target.setImageBitmap(cached);
            if (playback != null && track.uri.equals(playback.getCurrentUri())) updateTrackCoverBackground();
            return;
        }
        coverLoader.execute(() -> {
            Bitmap image = null;
            for (String url : artworkUrlCandidates(track.artworkUrl)) {
                try { image = decodeRemoteArtwork(url); }
                catch (Exception ignored) { }
                if (image != null) break;
            }
            if (image != null) artworkCache.put(track.uri, image);
            Bitmap decoded = image;
            if (decoded != null) runOnUiThread(() -> {
                if (track.uri.equals(target.getTag()) && artworkCache.get(track.uri) == decoded) {
                    target.setImageBitmap(decoded);
                    if (playback != null && track.uri.equals(playback.getCurrentUri())) updateTrackCoverBackground();
                }
            });
        });
    }

    private ArrayList<String> artworkUrlCandidates(String source) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (source == null || source.trim().isEmpty()) return new ArrayList<>();
        String url = source.trim();
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("i.ytimg.com/vi")) {
            int slash = url.lastIndexOf('/');
            if (slash > 0) {
                String folder = url.substring(0, slash + 1);
                candidates.add(folder + "maxresdefault.jpg");
                candidates.add(folder + "sddefault.jpg");
                candidates.add(folder + "hqdefault.jpg");
            }
        }
        if (lower.contains("sndcdn.com/")) {
            candidates.add(url.replaceAll("(?i)-large\\.", "-t500x500."));
            candidates.add(url.replaceAll("(?i)t\\d+x\\d+", "t500x500"));
        }
        if (lower.contains("deezer") || lower.contains("dzcdn"))
            candidates.add(url.replaceAll("(?i)/\\d{2,4}x\\d{2,4}(?=-)", "/1000x1000"));
        if (lower.contains("apple") || lower.contains("mzstatic"))
            candidates.add(url.replaceAll("(?i)/\\d{2,4}x\\d{2,4}bb", "/1000x1000bb"));
        candidates.add(url);
        return new ArrayList<>(candidates);
    }

    private Bitmap decodeRemoteArtwork(String url) throws Exception {
        java.net.URLConnection connection = new java.net.URL(url).openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Last Frequency");
        try (java.io.InputStream input = connection.getInputStream();
             java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read, total = 0;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > 16 * 1024 * 1024) throw new java.io.IOException("Обложка слишком большая");
                bytes.write(buffer, 0, read);
            }
            return decodeSampledArtwork(bytes.toByteArray(), 1200);
        }
    }

    private Uri publishDownloadedFile(java.io.File file, String selectedTree) {
        if (!selectedTree.isEmpty()) {
            try {
                Uri tree = Uri.parse(selectedTree);
                String rootDocumentId = DocumentsContract.getTreeDocumentId(tree);
                Uri rootDocument = DocumentsContract.buildDocumentUriUsingTree(tree, rootDocumentId);
                Uri destination = DocumentsContract.createDocument(getContentResolver(), rootDocument, "audio/mpeg", file.getName());
                if (destination == null) throw new java.io.IOException("Не удалось создать файл в выбранной папке.");
                try (java.io.InputStream input = new java.io.FileInputStream(file);
                     java.io.OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (output == null) throw new java.io.IOException("Нет доступа на запись в выбранную папку.");
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) >= 0) if (count > 0) output.write(buffer, 0, count);
                } catch (Exception error) {
                    try { DocumentsContract.deleteDocument(getContentResolver(), destination); } catch (Exception ignored) { }
                    throw error;
                }
                return destination;
            } catch (Exception error) {
                throw new IllegalStateException("Не удалось сохранить в выбранную папку. Выбери папку заново в настройках.", error);
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        Uri item = null;
        try {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, file.getName());
            values.put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
            values.put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_MUSIC + "/Last Frequency");
            values.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1);
            item = getContentResolver().insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (item == null) return null;
            try (java.io.InputStream input = new java.io.FileInputStream(file);
                 java.io.OutputStream output = getContentResolver().openOutputStream(item, "w")) {
                if (output == null) throw new java.io.IOException("Output stream is unavailable");
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) if (count > 0) output.write(buffer, 0, count);
            }
            android.content.ContentValues ready = new android.content.ContentValues();
            ready.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0);
            getContentResolver().update(item, ready, null, null);
            return item;
        } catch (Exception error) {
            if (item != null) try { getContentResolver().delete(item, null, null); } catch (Exception ignored) { }
            return null;
        }
    }

    private void renderPlaylistsPage() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        Button create = pill("＋ Создать плейлист", accentColor, textOnAccent());
        create.setOnClickListener(v -> createPlaylistDialog());
        content.addView(create, new LinearLayout.LayoutParams(-1, dp(44)));
        if (playlists.isEmpty()) {
            TextView empty = label("Собери музыку в свои плейлисты. Чтобы добавить трек, нажми на него и удерживай.", 14, mutedColor, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(22), dp(20), dp(22), dp(20));
            content.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            ListView list = new ListView(this);
            list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            list.setDividerHeight(dp(8));
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return playlists.size(); }
                @Override public Object getItem(int position) { return playlists.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    Playlist playlist = playlists.get(position);
                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(16), dp(12), dp(16), dp(12));
                    row.setBackground(roundRect(cardColor, dp(16), 0));
                    TextView icon = label("♫", 25, accentColor, true);
                    icon.setGravity(Gravity.CENTER);
                    row.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));
                    LinearLayout info = new LinearLayout(MainActivity.this);
                    info.setOrientation(LinearLayout.VERTICAL);
                    info.setPadding(dp(12), 0, 0, 0);
                    TextView name = label(playlist.name, 15, textColor, true);
                    TextView count = label(playlist.ids.size() + " треков", 12, mutedColor, false);
                    info.addView(name); info.addView(count);
                    row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
                    TextView more = label("···", 24, mutedColor, true);
                    more.setGravity(Gravity.CENTER);
                    row.addView(more, new LinearLayout.LayoutParams(dp(38), dp(44)));
                    more.setOnClickListener(v -> playlistMenu(playlist));
                    return row;
                }
            });
            list.setOnItemClickListener((parent, view, position, id) -> {
                selectedPlaylist = playlists.get(position).name;
                page = 0;
                renderPage();
            });
            list.setOnItemLongClickListener((parent, view, position, id) -> { playlistMenu(playlists.get(position)); return true; });
            content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        pageHost.addView(content, new FrameLayout.LayoutParams(-1, -1));
    }

    private void renderSettingsPage() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(5), 0, 0);
        content.addView(sectionTitle("БИБЛИОТЕКА"));
        settingsRow(content, "Добавить аудиофайлы", "Выбрать отдельные треки", "＋", this::chooseFiles);
        settingsRow(content, "Добавить папку", "Просканировать папку с музыкой", "▣", this::chooseFolder);
        settingsRow(content, "Порядок треков", sortLabel(), "↕", this::showSortDialog);
        content.addView(sectionTitle("ВНЕШНИЙ ВИД"));
        settingsRow(content, "Тема оформления", themeName(), "◐", this::showThemeDialog);
        boolean coverBackground = preferences.getBoolean("use_track_cover_background", false);
        settingsRow(content, "Обложка трека как фон", coverBackground ? "Включено · меняется вместе с треком" : "Выключено · фон будет обновляться при смене песни", "▧",
                () -> setTrackCoverBackgroundEnabled(!preferences.getBoolean("use_track_cover_background", false)));
        content.addView(sectionTitle("ОНЛАЙН И ЗАГРУЗКИ"));
        settingsRow(content, "Поиск и MP3", "YouTube, SoundCloud, Deezer и Apple Music", "⌕", () -> { page = 1; renderPage(); });
        settingsRow(content, "Импорт плейлиста", "Spotify, Apple Music, Яндекс Музыка и публичные ссылки", "⇩", this::showExternalImportDialog);
        String savedFolder = preferences.getString(KEY_DOWNLOAD_FOLDER, "");
        String folderDescription = savedFolder.isEmpty() ? "Музыка / Last Frequency · нажми, чтобы выбрать папку" : "Папка выбрана · нажми, чтобы изменить";
        settingsRow(content, "Куда сохранять MP3", folderDescription, "↓", this::chooseDownloadFolder);
        settingsRow(content, "Бас-буст", preferences.getInt("bass_boost_x10", 0) / 10f + "×", "♫", this::showBassBoostSettings);
        settingsRow(content, "Лицензии и исходный код", "yt-dlp for Android · GPL-3.0", "ⓘ", this::showLicenses);
        TextView note = label("Звук продолжает играть в фоне. На экране блокировки появятся название трека, исполнитель, обложка и кнопки управления.", 13, mutedColor, false);
        note.setLineSpacing(dp(4), 1f);
        note.setPadding(dp(14), dp(14), dp(14), dp(14));
        note.setBackground(roundRect(surfaceColor, dp(15), 0));
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(12);
        content.addView(note, noteParams);
        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.addView(content);
        pageHost.addView(settingsScroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showExternalImportDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setTextColor(textColor);
        input.setHintTextColor(mutedColor);
        input.setHint("Вставь ссылку на публичный плейлист");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setBackground(roundRect(surfaceColor, dp(13), 0));
        TextView note = label("Импортируются публичные плейлисты Spotify, Apple Music и Яндекс Музыки. Сами треки будут искать аудио для воспроизведения по названию.", 12, mutedColor, false);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(0, 0, 0, dp(12));
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(10), dp(20), 0);
        form.addView(note);
        form.addView(input, new LinearLayout.LayoutParams(-1, dp(48)));
        new AlertDialog.Builder(this).setTitle("Импорт плейлиста")
                .setView(form)
                .setPositiveButton("Импортировать", (dialog, which) -> importExternalPlaylist(input.getText().toString().trim()))
                .setNegativeButton("Отмена", null).show();
    }

    private void importExternalPlaylist(String url) {
        if (url.isEmpty()) { Toast.makeText(this, "Вставь ссылку на плейлист", Toast.LENGTH_SHORT).show(); return; }
        AlertDialog progress = new AlertDialog.Builder(this).setTitle("Импортирую плейлист")
                .setMessage("Загружаю список песен…")
                .setNegativeButton("Скрыть", null).create();
        progress.show();
        importer.execute(() -> {
            ArrayList<Track> imported = new ArrayList<>();
            String playlistName = "Импортированный плейлист";
            String failure = "";
            try {
                JSONObject result = PlatformCatalog.loadPlaylist(url);
                playlistName = result.optString("title", playlistName);
                String provider = result.optString("provider", PlatformCatalog.providerForUrl(url));
                JSONArray items = result.optJSONArray("tracks");
                if (items == null) throw new IllegalStateException("Площадка не вернула список песен.");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    String title = item.optString("title", "").trim();
                    String artist = item.optString("artist", "Неизвестный исполнитель").trim();
                    if (title.isEmpty()) continue;
                    String trackUrl = item.optString("url", "");
                    if (trackUrl.isEmpty()) trackUrl = "https://" + ("yandex".equals(provider) ? "music.yandex.ru" : "music.apple.com")
                            + "/search?text=" + java.net.URLEncoder.encode(artist + " " + title, "UTF-8");
                    Track track = new Track(trackUrl, title, artist, item.optString("album", playlistName),
                            item.optString("provider", provider), item.optString("thumbnail", result.optString("thumbnail", "")));
                    track.previewUrl = item.optString("preview_url", "");
                    track.duration = item.optInt("duration", 0);
                    boolean duplicate = false;
                    for (Track existing : imported) if (existing.uri.equals(track.uri)) { duplicate = true; break; }
                    if (!duplicate) imported.add(track);
                }
                if (imported.isEmpty()) throw new IllegalStateException("В публичном плейлисте не нашлось треков.");
            } catch (Exception error) {
                failure = error.getMessage() == null ? "Не удалось импортировать плейлист" : error.getMessage();
            }
            ArrayList<Track> ready = imported;
            String finalName = playlistName, finalFailure = failure;
            runOnUiThread(() -> {
                if (progress.isShowing()) progress.dismiss();
                if (!finalFailure.isEmpty()) {
                    Toast.makeText(this, "Импорт не удался: " + finalFailure, Toast.LENGTH_LONG).show();
                    return;
                }
                Playlist importedPlaylist = new Playlist(finalName);
                Playlist existingPlaylist = findPlaylist(finalName);
                if (existingPlaylist != null) importedPlaylist = existingPlaylist;
                HashSet<String> seenUris = new HashSet<>();
                for (Track incoming : ready) {
                    Track existing = findTrack(incoming.uri);
                    Track saved = existing == null ? incoming : existing;
                    if (existing == null) tracks.add(saved);
                    if (seenUris.add(saved.uri) && !importedPlaylist.ids.contains(saved.uri)) importedPlaylist.ids.add(saved.uri);
                }
                if (existingPlaylist == null) playlists.add(importedPlaylist);
                saveLibrary(); savePlaylists();
                selectedPlaylist = importedPlaylist.name;
                page = 0;
                renderPage();
                Toast.makeText(this, "Импортировано треков: " + importedPlaylist.ids.size(), Toast.LENGTH_LONG).show();
            });
        });
    }

    private void settingsRow(LinearLayout parent, String title, String detail, String icon, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(roundRect(cardColor, dp(14), 0));
        TextView glyph = label(icon, 22, accentColor, true);
        glyph.setGravity(Gravity.CENTER);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(38), dp(46)));
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setPadding(dp(9), 0, 0, 0);
        words.addView(label(title, 14, textColor, true));
        words.addView(label(detail, 12, mutedColor, false));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(64));
        params.bottomMargin = dp(8);
        parent.addView(row, params);
    }

    private TextView sectionTitle(String value) {
        TextView title = label(value, 10, mutedColor, true);
        title.setLetterSpacing(0.14f);
        title.setPadding(dp(4), dp(18), 0, dp(10));
        return title;
    }

    private void renderPlayerDock() {
        if (playerDock == null) return;
        playerDock.removeAllViews();
        String uri = playback == null ? "" : playback.getCurrentUri();
        Track track = findAnyTrack(uri);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        playerCover = new ImageView(this);
        playerCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        playerCover.setBackground(roundRect(surfaceColor, dp(12), 0));
        playerCover.setImageResource(android.R.drawable.ic_media_play);
        if (track != null) {
            Bitmap art = artwork(track);
            if (art != null) playerCover.setImageBitmap(art);
            else if (track.online) loadRemoteArtwork(track, playerCover);
        }
        top.addView(playerCover, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(9), 0, dp(3), 0);
        String currentTitle = track != null ? displayTrackTitle(track) : playback == null ? "LAST FREQUENCY" : playback.getCurrentTitle();
        String currentArtist = track != null ? track.artist : playback == null ? "Выбери трек в библиотеке" : playback.getCurrentArtist();
        trackTitle = label(currentTitle == null || currentTitle.isEmpty() ? "LAST FREQUENCY" : currentTitle, 14, textColor, true);
        trackArtist = label(currentArtist == null || currentArtist.isEmpty() ? "Выбери трек в библиотеке" : currentArtist, 11, mutedColor, false);
        trackTitle.setSingleLine(true); trackTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        trackArtist.setSingleLine(true); trackArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(trackTitle); info.addView(trackArtist);
        top.addView(info, new LinearLayout.LayoutParams(0, -1, 1));
        Button previous = transport("‹", () -> sendServiceAction(PlaybackService.ACTION_PREVIOUS));
        top.addView(previous, new LinearLayout.LayoutParams(dp(36), dp(44)));
        playerPlay = label(playback != null && playback.isPlaying() ? "Ⅱ" : "▶", 18, accentColor, true);
        playerPlay.setGravity(Gravity.CENTER);
        playerPlay.setBackground(roundRect(surfaceColor, dp(15), 0));
        playerPlay.setOnClickListener(v -> sendServiceAction(PlaybackService.ACTION_TOGGLE));
        top.addView(playerPlay, new LinearLayout.LayoutParams(dp(40), dp(40)));
        Button next = transport("›", () -> sendServiceAction(PlaybackService.ACTION_NEXT));
        top.addView(next, new LinearLayout.LayoutParams(dp(36), dp(44)));
        playerCover.setOnClickListener(v -> showNowPlaying());
        info.setOnClickListener(v -> showNowPlaying());
        top.setOnClickListener(v -> showNowPlaying());
        playerDock.addView(top, new LinearLayout.LayoutParams(-1, dp(54)));

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        progressRow.setPadding(dp(1), dp(1), 0, 0);
        dockTime = label("0:00", 10, mutedColor, false);
        progressRow.addView(dockTime, new LinearLayout.LayoutParams(dp(36), dp(30)));
        dockSeek = makeSeekBar(1000);
        progressRow.addView(dockSeek, new LinearLayout.LayoutParams(0, dp(30), 1));
        ImageView volumeIcon = new ImageView(this);
        volumeIcon.setScaleType(ImageView.ScaleType.CENTER);
        volumeIcon.setImageDrawable(new SpeakerIconDrawable(getResources().getDisplayMetrics().density, mutedColor));
        volumeIcon.setContentDescription("Громкость");
        progressRow.addView(volumeIcon, new LinearLayout.LayoutParams(dp(20), dp(30)));
        dockVolume = makeSeekBar(100);
        progressRow.addView(dockVolume, new LinearLayout.LayoutParams(dp(74), dp(30)));
        playerDock.addView(progressRow, new LinearLayout.LayoutParams(-1, dp(34)));
        dockSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (playback == null) return;
                int duration = playback.getDuration();
                if (duration > 0) playback.seekTo(duration * seekBar.getProgress() / 1000);
                if (nowPlayingSeek != null && duration > 0) nowPlayingSeek.setProgress(seekBar.getProgress());
            }
        });
        dockVolume.setProgress(playback == null ? preferences.getInt("player_volume", 100) : playback.getVolume());
        dockVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                preferences.edit().putInt("player_volume", progress).apply();
                if (playback != null) playback.setVolume(progress);
                if (nowPlayingVolume != null) nowPlayingVolume.setProgress(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) refreshNowPlayingTrack();
        updateTrackCoverBackground();
        updatePlaybackProgress();
        if (playerUiTicker == null) {
            playerUiTicker = new Runnable() {
                @Override public void run() {
                    updatePlaybackProgress();
                    if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) updateLyricsHighlight();
                    playerUiHandler.postDelayed(this, 500);
                }
            };
            playerUiHandler.post(playerUiTicker);
        }
    }

    private SeekBar nowPlayingVolume;

    private SeekBar makeSeekBar(int max) {
        SeekBar seek = new SeekBar(this);
        seek.setMax(max);
        seek.setPadding(dp(2), 0, dp(2), 0);
        if (Build.VERSION.SDK_INT >= 21) {
            seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accentColor));
            seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accentColor));
        }
        return seek;
    }

    private void updatePlaybackProgress() {
        if (playback == null) return;
        int duration = playback.getDuration();
        int position = playback.getCurrentPosition();
        int progress = duration <= 0 ? 0 : Math.max(0, Math.min(1000, (int) (1000L * position / duration)));
        if (dockSeek != null && !dockSeek.isPressed()) dockSeek.setProgress(progress);
        if (dockTime != null) dockTime.setText(formatTimeMs(position));
        if (nowPlayingSeek != null && !nowPlayingSeek.isPressed()) nowPlayingSeek.setProgress(progress);
        if (nowPlayingTime != null) nowPlayingTime.setText(formatTimeMs(position) + " / " + formatTimeMs(duration));
    }

    private String formatTimeMs(int milliseconds) {
        int seconds = Math.max(0, milliseconds / 1000);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void showNowPlaying() {
        if (playback == null || playback.getCurrentUri().isEmpty()) {
            Toast.makeText(this, "Сначала включи трек", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) return;
        nowPlayingTrack = findAnyTrack(playback.getCurrentUri());
        nowPlayingShowLyrics = false;
        FrameLayout frame = new FrameLayout(this);
        nowPlayingFrame = frame;
        frame.setBackground(trackCoverBackground());
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(24));
        page.addView(content);
        frame.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button close = pill("⌄", surfaceColor, textColor);
        close.setTextSize(23);
        close.setOnClickListener(v -> nowPlayingDialog.dismiss());
        top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(44)));
        TextView label = label("СЕЙЧАС ИГРАЕТ", 11, mutedColor, true);
        label.setGravity(Gravity.CENTER);
        top.addView(label, new LinearLayout.LayoutParams(0, dp(44), 1));
        content.addView(top, new LinearLayout.LayoutParams(-1, dp(48)));

        FrameLayout coverAndLyrics = new FrameLayout(this);
        float density = getResources().getDisplayMetrics().density;
        int screenHeightDp = Math.round(getResources().getDisplayMetrics().heightPixels / density);
        int screenWidthDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        int coverSizeDp = Math.min(300, Math.max(144, screenHeightDp - 470));
        coverSizeDp = Math.min(coverSizeDp, Math.max(144, screenWidthDp - 48));
        LinearLayout.LayoutParams mediaParams = new LinearLayout.LayoutParams(dp(coverSizeDp), dp(coverSizeDp));
        mediaParams.gravity = Gravity.CENTER_HORIZONTAL;
        mediaParams.topMargin = dp(12);
        mediaParams.bottomMargin = dp(12);
        content.addView(coverAndLyrics, mediaParams);
        nowPlayingCover = new ImageView(this);
        nowPlayingCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        nowPlayingCover.setBackground(roundRect(surfaceColor, dp(24), 0));
        nowPlayingCover.setImageResource(android.R.drawable.ic_media_play);
        coverAndLyrics.addView(nowPlayingCover, new FrameLayout.LayoutParams(-1, -1));
        nowPlayingLyricsScroll = new ScrollView(this);
        nowPlayingLyricsScroll.setVisibility(View.GONE);
        nowPlayingLyricsLines = new LinearLayout(this);
        nowPlayingLyricsLines.setGravity(Gravity.CENTER_HORIZONTAL);
        nowPlayingLyricsLines.setOrientation(LinearLayout.VERTICAL);
        nowPlayingLyricsLines.setPadding(dp(14), dp(24), dp(14), dp(24));
        nowPlayingLyricsScroll.addView(nowPlayingLyricsLines);
        nowPlayingLyricsScroll.setBackground(roundRect(surfaceColor, dp(24), 0));
        coverAndLyrics.addView(nowPlayingLyricsScroll, new FrameLayout.LayoutParams(-1, -1));

        nowPlayingTitle = label(nowPlayingTrack == null ? playback.getCurrentTitle() : displayTrackTitle(nowPlayingTrack), 23, textColor, true);
        nowPlayingTitle.setGravity(Gravity.CENTER);
        nowPlayingTitle.setMaxLines(2);
        nowPlayingTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(nowPlayingTitle, new LinearLayout.LayoutParams(-1, dp(62)));
        nowPlayingArtist = label(nowPlayingTrack == null ? playback.getCurrentArtist() : nowPlayingTrack.artist, 16, mutedColor, false);
        nowPlayingArtist.setGravity(Gravity.CENTER);
        content.addView(nowPlayingArtist, new LinearLayout.LayoutParams(-1, dp(30)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        Button lyrics = pill("♫  Текст песни", surfaceColor, textColor);
        lyrics.setTextSize(10.5f);
        actions.addView(lyrics, new LinearLayout.LayoutParams(dp(138), dp(34)));
        content.addView(actions, new LinearLayout.LayoutParams(-1, dp(38)));
        nowPlayingLyricsStatus = label("", 12, mutedColor, false);
        nowPlayingLyricsStatus.setGravity(Gravity.CENTER);
        nowPlayingLyricsStatus.setVisibility(View.GONE);
        content.addView(nowPlayingLyricsStatus, new LinearLayout.LayoutParams(-1, dp(26)));
        lyrics.setOnClickListener(v -> toggleNowPlayingLyrics());

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        Button previous = transport("‹", () -> sendServiceAction(PlaybackService.ACTION_PREVIOUS));
        controls.addView(previous, new LinearLayout.LayoutParams(dp(64), dp(54)));
        nowPlayingPlayButton = pill(playback.isPlaying() ? "Ⅱ" : "▶", accentColor, textOnAccent());
        nowPlayingPlayButton.setTextSize(22);
        controls.addView(nowPlayingPlayButton, new LinearLayout.LayoutParams(dp(74), dp(50)));
        nowPlayingPlayButton.setOnClickListener(v -> sendServiceAction(PlaybackService.ACTION_TOGGLE));
        Button next = transport("›", () -> sendServiceAction(PlaybackService.ACTION_NEXT));
        controls.addView(next, new LinearLayout.LayoutParams(dp(64), dp(54)));
        content.addView(controls, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        nowPlayingTime = label("0:00 / 0:00", 11, mutedColor, false);
        nowPlayingTime.setGravity(Gravity.CENTER);
        seekRow.addView(nowPlayingTime, new LinearLayout.LayoutParams(dp(86), dp(36)));
        nowPlayingSeek = makeSeekBar(1000);
        seekRow.addView(nowPlayingSeek, new LinearLayout.LayoutParams(0, dp(36), 1));
        content.addView(seekRow, new LinearLayout.LayoutParams(-1, dp(40)));
        nowPlayingSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (playback != null && playback.getDuration() > 0) playback.seekTo(playback.getDuration() * seekBar.getProgress() / 1000);
                if (dockSeek != null) dockSeek.setProgress(seekBar.getProgress());
            }
        });

        LinearLayout volumeRow = new LinearLayout(this);
        volumeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView volumeLabel = label("Громкость", 12, mutedColor, false);
        volumeRow.addView(volumeLabel, new LinearLayout.LayoutParams(dp(75), dp(36)));
        nowPlayingVolume = makeSeekBar(100);
        nowPlayingVolume.setProgress(playback.getVolume());
        volumeRow.addView(nowPlayingVolume, new LinearLayout.LayoutParams(0, dp(36), 1));
        content.addView(volumeRow, new LinearLayout.LayoutParams(-1, dp(40)));
        nowPlayingVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                preferences.edit().putInt("player_volume", progress).apply();
                if (playback != null) playback.setVolume(progress);
                if (dockVolume != null) dockVolume.setProgress(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        nowPlayingDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        nowPlayingDialog.setContentView(frame);
        nowPlayingDialog.setOnDismissListener(dialog -> {
            nowPlayingDialog = null; nowPlayingFrame = null; nowPlayingCover = null; nowPlayingSeek = null;
            nowPlayingVolume = null; nowPlayingLyricsScroll = null; nowPlayingLyricsLines = null;
            nowPlayingLyricsStatus = null; nowPlayingTime = null; nowPlayingTrack = null;
            nowPlayingTitle = null; nowPlayingArtist = null; nowPlayingPlayButton = null;
            nowPlayingLyrics.clear(); activeLyricLine = -1;
            nowPlayingLyricsKey = "";
        });
        nowPlayingDialog.show();
        android.view.Window window = nowPlayingDialog.getWindow();
        if (window != null) {
            window.setLayout(-1, -1);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        refreshNowPlayingTrack();
        updatePlaybackProgress();
    }

    private void refreshNowPlayingTrack() {
        if (playback == null || nowPlayingCover == null) return;
        String uri = playback.getCurrentUri();
        Track current = findAnyTrack(uri);
        boolean changed = nowPlayingTrack == null || !nowPlayingTrack.uri.equals(uri);
        if (current != null) nowPlayingTrack = current;
        else if (changed) {
            nowPlayingTrack = new Track(uri, playback.getCurrentTitle(), playback.getCurrentArtist(), "", "", playback.getCurrentCoverUrl());
            nowPlayingTrack.online = uri.startsWith("http://") || uri.startsWith("https://");
        }
        if (nowPlayingTrack == null) return;
        if (changed) {
            nowPlayingLyricsKey = "";
            nowPlayingLyrics.clear();
            activeLyricLine = -1;
            if (nowPlayingLyricsLines != null) nowPlayingLyricsLines.removeAllViews();
            if (nowPlayingLyricsStatus != null) { nowPlayingLyricsStatus.setText(""); nowPlayingLyricsStatus.setVisibility(View.GONE); }
            nowPlayingCover.setImageResource(android.R.drawable.ic_media_play);
        }
        if (nowPlayingTitle != null) nowPlayingTitle.setText(displayTrackTitle(nowPlayingTrack));
        if (nowPlayingArtist != null) nowPlayingArtist.setText(nowPlayingTrack.artist);
        if (nowPlayingPlayButton != null) nowPlayingPlayButton.setText(playback.isPlaying() ? "Ⅱ" : "▶");
        Bitmap art = artwork(nowPlayingTrack);
        if (art != null) nowPlayingCover.setImageBitmap(art);
        else if (nowPlayingTrack.online) loadRemoteArtwork(nowPlayingTrack, nowPlayingCover);
        updateTrackCoverBackground();
        if (changed && nowPlayingShowLyrics) loadNowPlayingLyrics();
    }

    private void toggleNowPlayingLyrics() {
        if (nowPlayingDialog == null || nowPlayingLyricsScroll == null) return;
        nowPlayingShowLyrics = !nowPlayingShowLyrics;
        nowPlayingCover.setVisibility(nowPlayingShowLyrics ? View.GONE : View.VISIBLE);
        nowPlayingLyricsScroll.setVisibility(nowPlayingShowLyrics ? View.VISIBLE : View.GONE);
        if (!nowPlayingShowLyrics && nowPlayingLyricsStatus != null) nowPlayingLyricsStatus.setVisibility(View.GONE);
        if (nowPlayingShowLyrics && nowPlayingLyrics.isEmpty()) loadNowPlayingLyrics();
    }

    private void loadNowPlayingLyrics() {
        Track track = nowPlayingTrack;
        if (track == null) { showLyricsError("Не удалось определить текущий трек."); return; }
        String key = track.title + "\n" + track.artist;
        if (key.equals(nowPlayingLyricsKey) && !nowPlayingLyrics.isEmpty()) return;
        nowPlayingLyricsKey = key;
        nowPlayingLyrics.clear(); activeLyricLine = -1;
        if (nowPlayingLyricsStatus != null) {
            nowPlayingLyricsStatus.setText("Ищу текст песни…");
            nowPlayingLyricsStatus.setVisibility(View.VISIBLE);
        }
        importer.execute(() -> {
            JSONObject result = null; String failure = "";
            try {
                String album = track.provider.isEmpty() ? track.album : "";
                int duration = track.duration > 0 ? track.duration : playback == null ? 0 : playback.getDuration() / 1000;
                result = OnlineCatalog.fetchLyrics(track.title, track.artist, album, duration);
            } catch (Exception error) { failure = error.getMessage() == null ? "Текст песни не найден." : error.getMessage(); }
            JSONObject fetched = result; String message = failure;
            runOnUiThread(() -> {
                if (nowPlayingDialog == null || !nowPlayingDialog.isShowing() || !key.equals(nowPlayingLyricsKey)) return;
                if (fetched == null) { showLyricsError(message); return; }
                String synced = fetched.optString("syncedLyrics", "");
                String plain = fetched.optString("plainLyrics", "");
                parseLyrics(synced, plain);
                if (nowPlayingLyricsStatus != null) {
                    nowPlayingLyricsStatus.setText("Текст песни не найден.");
                    nowPlayingLyricsStatus.setVisibility(nowPlayingLyrics.isEmpty() ? View.VISIBLE : View.GONE);
                }
                renderNowPlayingLyrics();
                updateLyricsHighlight();
            });
        });
    }

    private void parseLyrics(String synced, String plain) {
        nowPlayingLyrics.clear();
        java.util.regex.Pattern stamp = java.util.regex.Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]");
        if (synced != null) for (String line : synced.split("\\r?\\n")) {
            java.util.regex.Matcher matcher = stamp.matcher(line);
            ArrayList<Long> times = new ArrayList<>(); int end = 0;
            while (matcher.find()) {
                int minute = Integer.parseInt(matcher.group(1)); int second = Integer.parseInt(matcher.group(2));
                String fraction = matcher.group(3); int millis = fraction == null ? 0 : Integer.parseInt((fraction + "00").substring(0, 2)) * 10;
                times.add((minute * 60L + second) * 1000L + millis); end = matcher.end();
            }
            String text = line.substring(Math.min(end, line.length())).trim();
            if (text.isEmpty()) continue;
            for (Long time : times) nowPlayingLyrics.add(new LyricLine(time, text));
        }
        if (nowPlayingLyrics.isEmpty() && plain != null) for (String line : plain.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) nowPlayingLyrics.add(new LyricLine(-1, line.trim()));
        }
        Collections.sort(nowPlayingLyrics, Comparator.comparingLong(line -> line.timeMs < 0 ? Long.MAX_VALUE : line.timeMs));
    }

    private void renderNowPlayingLyrics() {
        if (nowPlayingLyricsLines == null) return;
        nowPlayingLyricsLines.removeAllViews();
        for (int i = 0; i < nowPlayingLyrics.size(); i++) {
            TextView line = label(nowPlayingLyrics.get(i).text, 18, mutedColor, false);
            line.setGravity(Gravity.CENTER);
            line.setPadding(dp(8), dp(9), dp(8), dp(9));
            nowPlayingLyricsLines.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void updateLyricsHighlight() {
        if (!nowPlayingShowLyrics || nowPlayingLyricsLines == null || nowPlayingLyrics.isEmpty()) return;
        int position = playback == null ? 0 : playback.getCurrentPosition();
        int active = -1;
        for (int i = 0; i < nowPlayingLyrics.size(); i++) {
            long time = nowPlayingLyrics.get(i).timeMs;
            if (time >= 0 && time <= position) active = i;
            else if (time >= 0 && time > position) break;
        }
        if (active == activeLyricLine) return;
        activeLyricLine = active;
        for (int i = 0; i < nowPlayingLyricsLines.getChildCount(); i++) {
            TextView row = (TextView) nowPlayingLyricsLines.getChildAt(i);
            boolean selected = i == active;
            row.setTextColor(selected ? accentColor : mutedColor);
            row.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            if (selected && nowPlayingLyricsScroll != null) nowPlayingLyricsScroll.post(() -> nowPlayingLyricsScroll.smoothScrollTo(0, Math.max(0, row.getTop() - nowPlayingLyricsScroll.getHeight() / 2)));
        }
    }

    private void showLyricsError(String message) {
        if (nowPlayingLyricsStatus != null) {
            nowPlayingLyricsStatus.setText(message == null || message.isEmpty() ? "Текст песни не найден." : message);
            nowPlayingLyricsStatus.setVisibility(View.VISIBLE);
        }
    }

    private void setTrackCoverBackgroundEnabled(boolean enabled) {
        preferences.edit().putBoolean("use_track_cover_background", enabled).apply();
        updateTrackCoverBackground();
        if (page == 3) renderPage();
        if (enabled) {
            Track current = playback == null ? null : findAnyTrack(playback.getCurrentUri());
            boolean available = current != null && (artwork(current) != null || (current.artworkUrl != null && !current.artworkUrl.isEmpty()));
            Toast.makeText(this, available ? "Фон из обложки включён" : "Включено · обложка появится, когда будет играть трек с изображением", Toast.LENGTH_LONG).show();
        } else Toast.makeText(this, "Фон из обложки выключен", Toast.LENGTH_SHORT).show();
    }

    private void updateTrackCoverBackground() {
        if (root != null) root.setBackground(trackCoverBackground());
        if (nowPlayingFrame != null) nowPlayingFrame.setBackground(trackCoverBackground());
    }

    private Drawable trackCoverBackground() {
        if (!preferences.getBoolean("use_track_cover_background", false) || playback == null) return themeBackground();
        Track current = findAnyTrack(playback.getCurrentUri());
        Bitmap cover = artwork(current);
        if (cover == null && current != null) cover = artworkCache.get(current.uri);
        return cover == null ? themeBackground() : new CoverBackgroundDrawable(cover);
    }

    private void showCoverCropDialog() {
        Track track = nowPlayingTrack;
        Bitmap source = track == null ? null : artworkCache.get(track.uri);
        if (source == null && track != null) source = artwork(track);
        if (source == null) {
            if (track != null && track.online && track.artworkUrl != null && !track.artworkUrl.isEmpty()) {
                Toast.makeText(this, "Загружаю исходную обложку…", Toast.LENGTH_SHORT).show();
                Track requested = track;
                coverLoader.execute(() -> {
                    Bitmap loaded = null;
                    for (String url : artworkUrlCandidates(requested.artworkUrl)) {
                        try { loaded = decodeRemoteArtwork(url); }
                        catch (Exception ignored) { }
                        if (loaded != null) break;
                    }
                    Bitmap decoded = loaded;
                    if (decoded != null) artworkCache.put(requested.uri, decoded);
                    runOnUiThread(() -> {
                        if (nowPlayingTrack == null || !requested.uri.equals(nowPlayingTrack.uri)) return;
                        if (decoded == null) {
                            Toast.makeText(this, "Не удалось загрузить обложку для обрезки.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (nowPlayingCover != null) nowPlayingCover.setImageBitmap(decoded);
                        updateTrackCoverBackground();
                        showCoverCropDialog();
                    });
                });
                return;
            }
            Toast.makeText(this, "Обложка ещё загружается или у трека нет изображения.", Toast.LENGTH_LONG).show();
            return;
        }
        final Bitmap original = source;
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(roundRect(surfaceColor, dp(16), 0));
        SeekBar zoom = makeSeekBar(100);
        SeekBar horizontal = makeSeekBar(100);
        SeekBar vertical = makeSeekBar(100);
        zoom.setProgress(0); horizontal.setProgress(50); vertical.setProgress(50);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setPadding(dp(20), dp(10), dp(20), 0);
        int previewSize = Math.min(dp(280), getResources().getDisplayMetrics().widthPixels - dp(72));
        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(previewSize, previewSize, Gravity.CENTER));
        form.addView(previewFrame, new LinearLayout.LayoutParams(-1, previewSize));
        form.addView(label("Масштаб · двигай изображение или используй ползунок", 12, mutedColor, false)); form.addView(zoom);
        form.addView(label("Положение по горизонтали", 12, mutedColor, false)); form.addView(horizontal);
        form.addView(label("Положение по вертикали", 12, mutedColor, false)); form.addView(vertical);
        Runnable refresh = () -> {
            Bitmap next = cropCover(original, zoom.getProgress(), horizontal.getProgress(), vertical.getProgress());
            android.graphics.drawable.Drawable oldDrawable = preview.getDrawable();
            Bitmap old = oldDrawable instanceof android.graphics.drawable.BitmapDrawable
                    ? ((android.graphics.drawable.BitmapDrawable) oldDrawable).getBitmap() : null;
            preview.setImageBitmap(next);
            if (old != null && old != original && old != next && !old.isRecycled()) old.recycle();
        };
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { refresh.run(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
        zoom.setOnSeekBarChangeListener(listener); horizontal.setOnSeekBarChangeListener(listener); vertical.setOnSeekBarChangeListener(listener);
        final float[] previousTouch = new float[2];
        preview.setOnTouchListener((view, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                previousTouch[0] = event.getX(); previousTouch[1] = event.getY(); return true;
            }
            if (event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                float width = Math.max(1, preview.getWidth()), height = Math.max(1, preview.getHeight());
                int dx = Math.round((event.getX() - previousTouch[0]) * 100f / width);
                int dy = Math.round((event.getY() - previousTouch[1]) * 100f / height);
                if (dx != 0) horizontal.setProgress(Math.max(0, Math.min(100, horizontal.getProgress() + dx)));
                if (dy != 0) vertical.setProgress(Math.max(0, Math.min(100, vertical.getProgress() + dy)));
                previousTouch[0] = event.getX(); previousTouch[1] = event.getY();
                return true;
            }
            return event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL;
        });
        refresh.run();
        ScrollView formScroll = new ScrollView(this);
        formScroll.setFillViewport(false);
        formScroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Обрезка обложки")
                .setView(formScroll).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Bitmap result = cropCover(original, zoom.getProgress(), horizontal.getProgress(), vertical.getProgress());
            try {
                java.io.File folder = new java.io.File(getFilesDir(), "covers");
                if (!folder.exists() && !folder.mkdirs()) throw new java.io.IOException("Не удалось создать папку обложек.");
                String id = Integer.toHexString(track.uri.hashCode());
                java.io.File output = new java.io.File(folder, id + ".jpg");
                try (java.io.FileOutputStream stream = new java.io.FileOutputStream(output)) {
                    result.compress(Bitmap.CompressFormat.JPEG, 96, stream);
                }
                track.artworkUrl = output.toURI().toString();
                artworkCache.put(track.uri, result);
                for (Track candidate : tracks) if (candidate.uri.equals(track.uri)) candidate.artworkUrl = track.artworkUrl;
                for (Track candidate : recentlyPlayed) if (candidate.uri.equals(track.uri)) candidate.artworkUrl = track.artworkUrl;
                for (Track candidate : onlineResults) if (candidate.uri.equals(track.uri)) candidate.artworkUrl = track.artworkUrl;
                saveLibrary(); saveRecentlyPlayed();
                if (nowPlayingCover != null) nowPlayingCover.setImageBitmap(result);
                renderPlayerDock();
                dialog.dismiss();
            } catch (Exception error) { Toast.makeText(this, "Не удалось сохранить обложку: " + error.getMessage(), Toast.LENGTH_LONG).show(); }
        }));
        dialog.show();
    }

    private Bitmap cropCover(Bitmap source, int zoomProgress, int horizontalProgress, int verticalProgress) {
        float zoom = 1f + zoomProgress / 100f * 1.5f;
        int base = Math.min(source.getWidth(), source.getHeight());
        int size = Math.max(1, Math.round(base / zoom));
        int maxX = source.getWidth() - size, maxY = source.getHeight() - size;
        float panX = (horizontalProgress - 50) / 50f, panY = (verticalProgress - 50) / 50f;
        int x = Math.max(0, Math.min(maxX, Math.round(maxX * (0.5f + panX * 0.5f))));
        int y = Math.max(0, Math.min(maxY, Math.round(maxY * (0.5f + panY * 0.5f))));
        Bitmap square = Bitmap.createBitmap(source, x, y, size, size);
        int outputSize = Math.min(1600, size);
        Bitmap output = square.getWidth() == outputSize ? square : Bitmap.createScaledBitmap(square, outputSize, outputSize, true);
        if (output != square) square.recycle();
        return output;
    }

    private Button transport(String glyph, Runnable action) {
        Button button = new Button(this);
        button.setText(glyph); button.setTextSize(25); button.setAllCaps(false);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setTextColor(textColor); button.setPadding(0, 0, 0, dp(3));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private final class TrackAdapter extends BaseAdapter {
        private final ArrayList<Track> rows;
        TrackAdapter(ArrayList<Track> rows) { this.rows = rows; }
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Track track = rows.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(11), dp(8), dp(7), dp(8));
            row.setBackground(roundRect(cardColor, dp(15), 0));
            ImageView cover = new ImageView(MainActivity.this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(roundRect(surfaceColor, dp(10), 0));
            cover.setImageResource(android.R.drawable.ic_media_play);
            Bitmap art = artwork(track);
            if (art != null) cover.setImageBitmap(art);
            else if (track.online) loadRemoteArtwork(track, cover);
            row.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout info = new LinearLayout(MainActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.CENTER_VERTICAL);
            info.setPadding(dp(11), 0, dp(4), 0);
            TextView title = label(displayTrackTitle(track), 14, textColor, true);
            title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView artist = label(track.artist, 12, mutedColor, false);
            artist.setSingleLine(true); artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(title); info.addView(artist);
            row.addView(info, new LinearLayout.LayoutParams(0, dp(52), 1));
            TextView more = label("···", 23, mutedColor, true); more.setGravity(Gravity.CENTER);
            row.addView(more, new LinearLayout.LayoutParams(dp(38), dp(48)));
            more.setOnClickListener(v -> showTrackPlaylistDialog(track));
            return row;
        }
    }

    private ArrayList<Track> visibleTracks() {
        ArrayList<Track> result = new ArrayList<>();
        Set<String> allowed = null;
        if (!selectedPlaylist.isEmpty()) {
            Playlist playlist = findPlaylist(selectedPlaylist);
            if (playlist != null) allowed = new HashSet<>(playlist.ids);
        }
        for (Track track : tracks) {
            if (allowed != null && !allowed.contains(track.uri)) continue;
            String searchable = (track.title + " " + track.artist + " " + track.album).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !searchable.contains(query)) continue;
            result.add(track);
        }
        if (selectedSort == 0) result.sort((a, b) -> {
            if (a.orderNumber > 0 && b.orderNumber > 0 && a.orderNumber != b.orderNumber) return Integer.compare(a.orderNumber, b.orderNumber);
            if (a.orderNumber > 0 && b.orderNumber == 0) return -1;
            if (b.orderNumber > 0 && a.orderNumber == 0) return 1;
            return a.title.toLowerCase(Locale.ROOT).compareTo(b.title.toLowerCase(Locale.ROOT));
        });
        else if (selectedSort == 1) result.sort(Comparator.comparing(t -> t.artist.toLowerCase(Locale.ROOT)));
        else if (selectedSort == 2) result.sort(Comparator.comparing(t -> t.album.toLowerCase(Locale.ROOT)));
        return result;
    }

    private void playTrack(ArrayList<Track> rows, int index) {
        if (index < 0 || index >= rows.size()) return;
        if (rows.get(index).online) { playOnline(rows.get(index)); return; }
        try {
            JSONArray queue = new JSONArray();
            int queueIndex = 0;
            for (Track track : rows) {
                if (track.online) continue;
                JSONObject item = new JSONObject();
                item.put("uri", track.uri); item.put("title", track.title);
                item.put("artist", track.artist); item.put("album", track.album);
                queue.put(item);
                if (track == rows.get(index)) queueIndex = queue.length() - 1;
            }
            startQueue(queue, queueIndex);
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось запустить трек", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendServiceAction(String action) {
        Intent intent = new Intent(this, PlaybackService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && PlaybackService.ACTION_SET_QUEUE.equals(action)) startForegroundService(intent);
        else startService(intent);
    }

    private void chooseFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_FILES);
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_FOLDER);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DOWNLOAD_FOLDER) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            Uri tree = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(tree, flags); }
            catch (SecurityException ignored) { }
            preferences.edit().putString(KEY_DOWNLOAD_FOLDER, tree.toString()).apply();
            Toast.makeText(this, "Папка загрузок сохранена для следующих треков.", Toast.LENGTH_LONG).show();
            renderPage();
            return;
        }
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQUEST_FILES) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) uris.add(data.getData());
            takeUriPermissions(uris, data.getFlags());
            importUris(uris);
        } else if (requestCode == REQUEST_FOLDER && data.getData() != null) {
            Uri tree = data.getData();
            try { getContentResolver().takePersistableUriPermission(tree, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { }
            Toast.makeText(this, "Сканирую папку…", Toast.LENGTH_SHORT).show();
            importer.execute(() -> {
                ArrayList<Uri> found = new ArrayList<>();
                try { scanFolder(tree, DocumentsContract.getTreeDocumentId(tree), found, 0); }
                catch (Exception ignored) { }
                runOnUiThread(() -> importUris(found));
            });
        }
    }

    private void chooseDownloadFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        String current = preferences.getString(KEY_DOWNLOAD_FOLDER, "");
        if (!current.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(current)); } catch (Exception ignored) { }
        }
        startActivityForResult(intent, REQUEST_DOWNLOAD_FOLDER);
    }

    private void takeUriPermissions(ArrayList<Uri> uris, int flags) {
        int read = flags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (read == 0) read = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        for (Uri uri : uris) try { getContentResolver().takePersistableUriPermission(uri, read); }
        catch (SecurityException ignored) { }
    }

    private void scanFolder(Uri tree, String documentId, ArrayList<Uri> result, int depth) {
        if (depth > 20 || result.size() >= 10000) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = getContentResolver().query(children, columns, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext() && result.size() < 10000) {
                String childId = cursor.getString(0), mime = cursor.getString(1);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) scanFolder(tree, childId, result, depth + 1);
                else if (mime != null && mime.startsWith("audio/")) {
                    result.add(DocumentsContract.buildDocumentUriUsingTree(tree, childId));
                }
            }
        } catch (Exception ignored) { }
    }

    private void importUris(ArrayList<Uri> uris) {
        if (uris.isEmpty()) { Toast.makeText(this, "Аудиофайлы не найдены", Toast.LENGTH_SHORT).show(); return; }
        HashSet<String> existing = new HashSet<>();
        for (Track track : tracks) existing.add(track.uri);
        importer.execute(() -> {
            ArrayList<Track> imported = new ArrayList<>();
            HashSet<String> seen = new HashSet<>(existing);
            for (Uri uri : uris) {
                if (!seen.add(uri.toString())) continue;
                Track track = readTrack(uri);
                if (track != null) imported.add(track);
            }
            runOnUiThread(() -> {
                tracks.addAll(imported);
                saveLibrary();
                renderPage();
                Toast.makeText(this, imported.isEmpty() ? "Новых треков не найдено" : "Добавлено треков: " + imported.size(), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private Track readTrack(Uri uri) {
        String fallback = displayName(uri);
        String title = fallback, artist = "Неизвестный исполнитель", album = "";
        int orderNumber = 0;
        java.util.regex.Matcher filename = java.util.regex.Pattern.compile("^\\s*(\\d{1,4})\\s*[-–—.]\\s*(.+?)\\s*[-–—]\\s*(.+)$").matcher(fallback);
        String filenameArtist = "";
        String filenameTitle = "";
        if (filename.matches()) {
            try { orderNumber = Integer.parseInt(filename.group(1)); } catch (NumberFormatException ignored) { }
            filenameArtist = filename.group(2).trim();
            filenameTitle = filename.group(3).trim();
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (value != null && !value.trim().isEmpty()) title = value.trim();
            value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (value != null && !value.trim().isEmpty()) artist = value.trim();
            value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (value != null) album = value.trim();
            value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
            if (value != null) {
                java.util.regex.Matcher number = java.util.regex.Pattern.compile("^\\s*(\\d+)").matcher(value);
                if (number.find()) try { orderNumber = Integer.parseInt(number.group(1)); } catch (NumberFormatException ignored) { }
            }
        } catch (Exception ignored) { }
        finally { try { retriever.release(); } catch (Exception ignored) { } }
        if ("Неизвестный исполнитель".equals(artist) && !filenameArtist.isEmpty()) artist = filenameArtist;
        if ((!filenameTitle.isEmpty() && (title.equals(fallback) || title.matches("^\\d{1,4}\\s*[-–—].*")))) title = filenameTitle;
        Track result = new Track(uri.toString(), title, artist, album);
        result.orderNumber = orderNumber;
        return result;
    }

    private String displayName(Uri uri) {
        String name = "Без названия";
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (Exception ignored) { }
        if (name == null) return "Без названия";
        int dot = name.lastIndexOf('.');
        String clean = dot > 0 ? name.substring(0, dot) : name;
        return clean.replace('_', ' ').trim();
    }

    private String displayTrackTitle(Track track) {
        if (track == null) return "";
        if (track.orderNumber <= 0) return track.title;
        String prefix = track.orderNumber < 10 ? "0" + track.orderNumber : String.valueOf(track.orderNumber);
        return prefix + " - " + (track.artist == null || track.artist.isEmpty() ? "" : track.artist + " - ") + track.title;
    }

    private void normalizeTrackMetadata(Track track) {
        if (track == null || track.title == null) return;
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("^\\s*(\\d{1,4})\\s*[-–—.]\\s*(.+?)\\s*[-–—]\\s*(.+)$").matcher(track.title);
        if (!match.matches()) return;
        if (track.orderNumber <= 0) try { track.orderNumber = Integer.parseInt(match.group(1)); } catch (NumberFormatException ignored) { }
        if (track.artist == null || track.artist.isEmpty() || "Неизвестный исполнитель".equals(track.artist)) track.artist = match.group(2).trim();
        track.title = match.group(3).trim();
    }

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] options = {"По названию", "По исполнителю", "По альбому", "Порядок добавления"};
        for (int i = 0; i < options.length; i++) menu.getMenu().add(0, i, i, options[i]);
        menu.setOnMenuItemClickListener(item -> {
            selectedSort = item.getItemId(); preferences.edit().putInt("sort", selectedSort).apply();
            renderPage(); return true;
        });
        menu.show();
    }

    private void showSortDialog() {
        new AlertDialog.Builder(this).setTitle("Сортировать треки")
                .setSingleChoiceItems(new String[]{"По названию", "По исполнителю", "По альбому", "Порядок добавления"}, selectedSort,
                        (dialog, which) -> { selectedSort = which; preferences.edit().putInt("sort", which).apply(); dialog.dismiss(); renderPage(); })
                .setNegativeButton("Отмена", null).show();
    }

    private String sortLabel() {
        String[] options = {"Название A—Я", "Исполнитель", "Альбом", "Добавление"};
        return options[Math.max(0, Math.min(selectedSort, options.length - 1))];
    }

    private void createPlaylistDialog() {
        createPlaylistDialog(null);
    }

    private void createPlaylistDialog(Track trackToAdd) {
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setHint("Название плейлиста"); input.setTextColor(textColor); input.setHintTextColor(mutedColor);
        input.setPadding(dp(14), 0, dp(14), 0); input.setBackground(roundRect(surfaceColor, dp(12), 0));
        LinearLayout wrapper = new LinearLayout(this); wrapper.setPadding(dp(22), dp(8), dp(22), 0); wrapper.addView(input, new LinearLayout.LayoutParams(-1, dp(48)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Новый плейлист").setView(wrapper)
                .setNegativeButton("Отмена", null).setPositiveButton("Создать", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) { input.setError("Введи название"); return; }
            if (findPlaylist(name) != null) { input.setError("Такой плейлист уже есть"); return; }
            Playlist playlist = new Playlist(name);
            if (trackToAdd != null && !playlist.ids.contains(trackToAdd.uri)) playlist.ids.add(trackToAdd.uri);
            playlists.add(playlist); savePlaylists(); dialog.dismiss(); renderPage();
            if (trackToAdd != null) Toast.makeText(this, "Трек добавлен в «" + playlist.name + "»", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private void showTrackPlaylistDialog(Track track) {
        if (track.online) track = ensureOnlineTrackInLibrary(track);
        String[] names = new String[playlists.size() + 1];
        for (int i = 0; i < playlists.size(); i++) names[i] = playlists.get(i).name;
        names[names.length - 1] = "＋ Создать новый плейлист";
        Track playlistTrack = track;
        new AlertDialog.Builder(this).setTitle("Добавить в плейлист")
                .setItems(names, (dialog, which) -> {
                    if (which == names.length - 1) { createPlaylistDialog(playlistTrack); return; }
                    Playlist playlist = playlists.get(which);
                    if (!playlist.ids.contains(playlistTrack.uri)) playlist.ids.add(playlistTrack.uri);
                    savePlaylists(); renderPage();
                    Toast.makeText(this, "Трек добавлен в «" + playlist.name + "»", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null).show();
    }

    private Track ensureOnlineTrackInLibrary(Track track) {
        Track saved = findTrack(track.uri);
        if (saved != null) return saved;
        saved = new Track(track.uri, track.title, track.artist, track.album, track.provider, track.artworkUrl);
        saved.online = true;
        saved.previewOnly = track.previewOnly;
        saved.previewUrl = track.previewUrl;
        saved.duration = track.duration;
        saved.orderNumber = track.orderNumber;
        tracks.add(saved);
        saveLibrary();
        return saved;
    }

    private void playlistMenu(Playlist playlist) {
        String[] options = {"Открыть", "Переименовать", "Удалить"};
        new AlertDialog.Builder(this).setTitle(playlist.name).setItems(options, (dialog, which) -> {
            if (which == 0) { selectedPlaylist = playlist.name; page = 0; renderPage(); }
            else if (which == 1) renamePlaylistDialog(playlist);
            else new AlertDialog.Builder(this).setMessage("Удалить плейлист «" + playlist.name + "»?")
                    .setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d, w) -> {
                        playlists.remove(playlist); if (selectedPlaylist.equals(playlist.name)) selectedPlaylist = "";
                        savePlaylists(); renderPage();
                    }).show();
        }).show();
    }

    private void renamePlaylistDialog(Playlist playlist) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(playlist.name);
        new AlertDialog.Builder(this).setTitle("Переименовать плейлист").setView(input)
                .setNegativeButton("Отмена", null).setPositiveButton("Сохранить", (dialog, which) -> {
                    String oldName = playlist.name, newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        playlist.name = newName;
                        if (selectedPlaylist.equals(oldName)) selectedPlaylist = newName;
                        savePlaylists(); renderPage();
                    }
                }).show();
    }

    private void showThemeDialog() {
        new AlertDialog.Builder(this).setTitle("Тема оформления")
                .setSingleChoiceItems(THEME_NAMES, selectedTheme,
                        (dialog, which) -> {
                            selectedTheme = which;
                            preferences.edit().putInt("theme", which).apply();
                            dialog.dismiss();
                            if (which == THEME_NAMES.length - 1) showCustomThemeEditor();
                            else recreate();
                        }).setNegativeButton("Готово", null).show();
    }

    private void showBassBoostSettings() {
        int initial = preferences.getInt("bass_boost_x10", 0);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(12), dp(22), dp(4));
        TextView value = label(String.format(Locale.ROOT, "%.1f×", initial / 10f), 22, accentColor, true);
        value.setGravity(Gravity.CENTER);
        SeekBar strength = makeSeekBar(100);
        strength.setProgress(initial);
        TextView note = label("Усиливает низкие частоты. Максимум — 10×; эффект зависит от аудиосистемы телефона.", 12, mutedColor, false);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(0, dp(7), 0, 0);
        form.addView(value, new LinearLayout.LayoutParams(-1, dp(36)));
        form.addView(strength, new LinearLayout.LayoutParams(-1, dp(42)));
        form.addView(note, new LinearLayout.LayoutParams(-1, -2));
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value.setText(String.format(Locale.ROOT, "%.1f×", progress / 10f));
                if (fromUser) applyBassBoost(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Бас-буст")
                .setView(form)
                .setPositiveButton("Готово", (d, which) -> applyBassBoost(strength.getProgress()))
                .setNegativeButton("Выключить", (d, which) -> applyBassBoost(0)).create();
        dialog.setOnDismissListener(d -> { if (page == 3) renderPage(); });
        dialog.show();
    }

    private void applyBassBoost(int strengthX10) {
        int value = Math.max(0, Math.min(100, strengthX10));
        preferences.edit().putInt("bass_boost_x10", value).apply();
        if (playback != null) playback.setBassBoostX10(value);
    }

    private void showCustomThemeEditor() {
        String[] keys = {"bg1", "bg2", "bg3", "panel", "text", "muted", "accent"};
        String[] labels = {"Фон · верх", "Фон · середина", "Фон · низ", "Панели", "Текст", "Вторичный текст", "Акцент"};
        String[] defaults = THEME_PALETTES[THEME_PALETTES.length - 1];
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText[] fields = new EditText[keys.length];
        for (int i = 0; i < keys.length; i++) {
            TextView title = label(labels[i], 12, mutedColor, true);
            title.setPadding(0, dp(8), 0, dp(4));
            form.addView(title);
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setTextColor(textColor);
            input.setHintTextColor(mutedColor);
            input.setHint("#RRGGBB");
            input.setText(preferences.getString("custom_" + keys[i], defaults[i]));
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            input.setPadding(dp(12), 0, dp(12), 0);
            input.setBackground(roundRect(surfaceColor, dp(10), 0));
            form.addView(input, new LinearLayout.LayoutParams(-1, dp(42)));
            fields[i] = input;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Своя тема")
                .setView(scroll).setNegativeButton("Отмена", (d, w) -> recreate())
                .setPositiveButton("Сохранить", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                SharedPreferences.Editor editor = preferences.edit().putInt("theme", selectedTheme);
                for (int i = 0; i < fields.length; i++) {
                    String color = fields[i].getText().toString().trim();
                    if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException(labels[i] + ": введи цвет в формате #RRGGBB");
                    Color.parseColor(color);
                    editor.putString("custom_" + keys[i], color);
                }
                editor.apply();
                dialog.dismiss();
                recreate();
            } catch (IllegalArgumentException error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }));
        dialog.show();
    }

    private void showLicenses() {
        try (java.io.InputStream input = getAssets().open("LICENSE")) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) if (count > 0) output.write(buffer, 0, count);
            String license = "LAST FREQUENCY Android · 2026\n" +
                    "Мобильный исходный код проекта поставляется в папке phone app/.\n" +
                    "В приложении используется yt-dlp for Android и FFmpeg. Приложение распространяется на условиях GPL-3.0; гарантия на программу не предоставляется.\n\n" +
                    "Источник зависимости: github.com/yausername/youtubedl-android\n\n" +
                    output.toString("UTF-8");
            TextView text = label(license, 11, textColor, false);
            text.setGravity(Gravity.TOP);
            text.setPadding(dp(14), dp(10), dp(14), dp(10));
            ScrollView scroll = new ScrollView(this);
            scroll.addView(text);
            new AlertDialog.Builder(this).setTitle("LAST FREQUENCY · лицензии")
                    .setView(scroll).setPositiveButton("Закрыть", null).show();
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось открыть текст лицензии", Toast.LENGTH_SHORT).show();
        }
    }

    private String themeName() {
        return THEME_NAMES[Math.max(0, Math.min(THEME_NAMES.length - 1, selectedTheme))];
    }

    private void migrateThemeSelection() {
        if (preferences.getInt("theme_palette_version", 0) >= 1) return;
        int[] oldToCurrent = {0, 0, 9, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        if (selectedTheme >= 0 && selectedTheme < oldToCurrent.length) selectedTheme = oldToCurrent[selectedTheme];
        else selectedTheme = 0;
        preferences.edit().putInt("theme", selectedTheme).putInt("theme_palette_version", 1).apply();
    }

    private void applyPalette() {
        int index = Math.max(0, Math.min(THEME_PALETTES.length - 1, selectedTheme));
        String[] palette = THEME_PALETTES[index].clone();
        if (index == THEME_PALETTES.length - 1) {
            String[] keys = {"bg1", "bg2", "bg3", "panel", "text", "muted", "accent"};
            for (int i = 0; i < keys.length; i++) palette[i] = preferences.getString("custom_" + keys[i], palette[i]);
        }
        try {
            gradientColors = new int[]{Color.parseColor(palette[0]), Color.parseColor(palette[1]), Color.parseColor(palette[2])};
            backgroundColor = gradientColors[0];
            surfaceColor = Color.parseColor(palette[3]);
            cardColor = gradientColors[1];
            textColor = Color.parseColor(palette[4]);
            mutedColor = Color.parseColor(palette[5]);
            accentColor = Color.parseColor(palette[6]);
        } catch (IllegalArgumentException ignored) {
            selectedTheme = 0;
            preferences.edit().putInt("theme", 0).apply();
            applyPalette();
        }
    }

    private int textOnAccent() {
        double luminance = (0.299 * Color.red(accentColor) + 0.587 * Color.green(accentColor) + 0.114 * Color.blue(accentColor)) / 255d;
        return luminance > 0.68 ? Color.rgb(22, 20, 27) : Color.WHITE;
    }

    private GradientDrawable themeBackground() {
        int[] colors = gradientColors == null ? new int[]{backgroundColor, backgroundColor, backgroundColor} : gradientColors;
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
    }

    private boolean isLightColor(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255d;
        return luminance > 0.7;
    }

    private void readLibrary() {
        tracks.clear();
        try {
            JSONArray saved = new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LIBRARY, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) continue;
                String uri = item.optString("uri", "");
                if (!uri.isEmpty()) {
                    Track track = new Track(uri, item.optString("title", "Без названия"),
                            item.optString("artist", "Неизвестный исполнитель"), item.optString("album", ""),
                            item.optString("provider", ""), item.optString("artworkUrl", ""));
                    track.online = item.optBoolean("online", false);
                    track.previewOnly = item.optBoolean("previewOnly", false);
                    track.previewUrl = item.optString("previewUrl", "");
                    track.orderNumber = item.optInt("orderNumber", 0);
                    normalizeTrackMetadata(track);
                    tracks.add(track);
                }
            }
        } catch (Exception ignored) { }
    }

    private void saveLibrary() {
        JSONArray saved = new JSONArray();
        try {
            for (Track track : tracks) {
                JSONObject item = new JSONObject(); item.put("uri", track.uri); item.put("title", track.title);
                item.put("artist", track.artist); item.put("album", track.album);
                item.put("provider", track.provider); item.put("artworkUrl", track.artworkUrl); item.put("online", track.online);
                item.put("previewOnly", track.previewOnly); item.put("previewUrl", track.previewUrl);
                item.put("orderNumber", track.orderNumber);
                saved.put(item);
            }
            preferences.edit().putString(KEY_LIBRARY, saved.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void readPlaylists() {
        playlists.clear();
        try {
            JSONArray saved = new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PLAYLISTS, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.optJSONObject(i); if (item == null) continue;
                Playlist playlist = new Playlist(item.optString("name", "Плейлист"));
                JSONArray ids = item.optJSONArray("ids");
                if (ids != null) for (int j = 0; j < ids.length(); j++) playlist.ids.add(ids.optString(j));
                playlists.add(playlist);
            }
        } catch (Exception ignored) { }
    }

    private void savePlaylists() {
        JSONArray saved = new JSONArray();
        try {
            for (Playlist playlist : playlists) {
                JSONObject item = new JSONObject(); item.put("name", playlist.name);
                JSONArray ids = new JSONArray(); for (String id : playlist.ids) ids.put(id);
                item.put("ids", ids); saved.put(item);
            }
            preferences.edit().putString(KEY_PLAYLISTS, saved.toString()).apply();
        } catch (Exception ignored) { }
    }

    private Playlist findPlaylist(String name) {
        for (Playlist playlist : playlists) if (playlist.name.equalsIgnoreCase(name)) return playlist;
        return null;
    }

    private Track findTrack(String uri) {
        if (uri == null || uri.isEmpty()) return null;
        for (Track track : tracks) if (track.uri.equals(uri)) return track;
        return null;
    }

    private Track findAnyTrack(String uri) {
        Track track = findTrack(uri);
        if (track != null) return track;
        if (uri != null) for (Track item : onlineResults) if (item.uri.equals(uri)) return item;
        if (uri != null) for (Track item : recentlyPlayed) if (item.uri.equals(uri)) return item;
        return null;
    }

    private Bitmap artwork(Track track) {
        if (track == null) return null;
        Bitmap cached = artworkCache.get(track.uri);
        if (cached != null) return cached;
        if (track.artworkUrl != null && track.artworkUrl.startsWith("file:")) {
            try {
                String path = Uri.parse(track.artworkUrl).getPath();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, bounds);
                BitmapFactory.Options options = sampledOptions(bounds.outWidth, bounds.outHeight, 1200);
                Bitmap local = BitmapFactory.decodeFile(path, options);
                if (local != null) {
                    artworkCache.put(track.uri, local);
                    return local;
                }
            } catch (Exception ignored) { }
        }
        if (track.online) return null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, Uri.parse(track.uri));
            byte[] data = retriever.getEmbeddedPicture();
            if (data == null || data.length == 0) return null;
            Bitmap result = decodeSampledArtwork(data, 1200);
            if (result == null) return null;
            artworkCache.put(track.uri, result);
            return result;
        } catch (Exception ignored) { return null; }
        finally { try { retriever.release(); } catch (Exception ignored) { } }
    }

    private Bitmap decodeSampledArtwork(byte[] data, int maximumDimension) {
        if (data == null || data.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        BitmapFactory.Options options = sampledOptions(bounds.outWidth, bounds.outHeight, maximumDimension);
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private BitmapFactory.Options sampledOptions(int width, int height, int maximumDimension) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        if (width <= 0 || height <= 0) return options;
        int sample = 1;
        int largest = Math.max(width, height);
        while (largest / sample > maximumDimension) sample *= 2;
        options.inSampleSize = sample;
        return options;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button pill(String text, int fill, int color) {
        Button button = new Button(this);
        button.setText(text); button.setTextSize(12); button.setAllCaps(false); button.setTextColor(color);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(roundRect(fill, dp(14), 0));
        return button;
    }

    private GradientDrawable roundRect(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(radius);
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
