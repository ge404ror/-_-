package com.lastfrequency.player;

import android.content.Context;
import android.os.Environment;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/** Online track search, stream resolution, and MP3 downloads for supported public sources. */
public final class OnlineCatalog {
    private static final ExecutorService GLOBAL_SEARCH_POOL = Executors.newFixedThreadPool(3);
    private static final Pattern NON_MUSIC = Pattern.compile(
            "\\b(reaction|reacts|review|tutorial|how to|gameplay|walkthrough|podcast|news|interview|vlog|shorts?|trailer|full movie|episode|documentary|обзор|реакци\\w*|урок\\w*|обучени\\w*|прохождени\\w*|подкаст\\w*|новост\\w*|интервью|влог\\w*|трейлер|фильм|серия|документал\\w*)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private OnlineCatalog() { }

    public interface ProgressListener { void onProgress(float percent); }

    public static final class SearchResponse {
        public final ArrayList<JSONObject> items = new ArrayList<>();
        public final ArrayList<String> errors = new ArrayList<>();
    }

    private static final class ProviderSearch {
        final String provider;
        final ArrayList<JSONObject> items;
        final String error;
        ProviderSearch(String provider, ArrayList<JSONObject> items, String error) {
            this.provider = provider; this.items = items; this.error = error;
        }
    }

    public static void initialize(Context context) throws YoutubeDLException {
        YoutubeDL.getInstance().init(context.getApplicationContext());
        FFmpeg.getInstance().init(context.getApplicationContext());
    }

    public static ArrayList<JSONObject> search(String query, String provider) throws Exception {
        ArrayList<JSONObject> results = new ArrayList<>();
        String clean = query == null ? "" : query.trim();
        if (clean.isEmpty()) return results;
        String selected = provider == null ? "all" : provider.toLowerCase(Locale.ROOT);
        String[] providers = "all".equals(selected)
                ? new String[]{"youtube_music", "youtube", "soundcloud", "deezer", "apple"}
                : new String[]{selected};
        Set<String> seen = new HashSet<>();
        for (String source : providers) {
            if ("deezer".equals(source) || "apple".equals(source)) {
                try {
                    int limit = "all".equals(selected) ? 4 : 20;
                    for (JSONObject item : searchPreviewCatalog(clean, source, limit)) {
                        if (seen.add(item.optString("url", ""))) results.add(item);
                    }
                } catch (Exception ignored) { }
                continue;
            }
            if (!("youtube_music".equals(source) || "youtube".equals(source) || "soundcloud".equals(source))) continue;
            String searchUrl;
            if (clean.startsWith("https://") || clean.startsWith("http://")) {
                searchUrl = clean;
            } else if ("soundcloud".equals(source)) {
                searchUrl = "scsearch20:" + clean;
            } else if ("youtube_music".equals(source)) {
                searchUrl = "https://music.youtube.com/search?q=" +
                        URLEncoder.encode(clean, StandardCharsets.UTF_8.name()) + "#songs";
            } else {
                searchUrl = "ytsearch20:" + clean + " music";
            }
            YoutubeDLRequest request = new YoutubeDLRequest(searchUrl);
            request.addOption("--dump-single-json");
            request.addOption("--flat-playlist");
            request.addOption("--skip-download");
            request.addOption("--no-warnings");
            request.addOption("--ignore-errors");
            request.addOption("--playlist-end", "20");
            request.addOption("--socket-timeout", "15");
            try {
                String output = YoutubeDL.getInstance().execute(request).getOut();
                if (output == null || output.trim().isEmpty()) continue;
                JSONObject root = new JSONObject(output.trim());
                JSONArray entries = root.optJSONArray("entries");
                int providerLimit = "all".equals(selected) ? 4 : 20;
                int addedFromProvider = 0;
                if (entries == null) {
                    JSONObject single = toResult(root, source);
                    if (single != null && seen.add(single.optString("url", ""))) { results.add(single); addedFromProvider++; }
                } else {
                    for (int i = 0; i < entries.length() && addedFromProvider < providerLimit; i++) {
                        JSONObject entry = entries.optJSONObject(i);
                        if (entry == null) continue;
                        JSONObject item = toResult(entry, source);
                        if (item == null) continue;
                        String url = item.optString("url", "");
                        if (seen.add(url)) { results.add(item); addedFromProvider++; }
                    }
                }
            } catch (Exception sourceError) {
                if (!"all".equals(selected)) throw sourceError;
            }
            if (results.size() >= 20) break;
        }
        return new ArrayList<>(results.subList(0, Math.min(20, results.size())));
    }

    /** Mirrors the desktop global search: debounced UI, provider fan-out, playlist mode, and ranked results. */
    public static SearchResponse search(String query, String provider, String kind) throws Exception {
        String clean = query == null ? "" : query.trim();
        String selected = provider == null ? "all" : provider.toLowerCase(Locale.ROOT);
        String searchKind = "playlist".equalsIgnoreCase(kind) ? "playlist" : "track";
        SearchResponse response = new SearchResponse();
        if (clean.isEmpty()) return response;
        if (clean.startsWith("https://") || clean.startsWith("http://")) {
            if (!PlatformCatalog.providerForUrl(clean).isEmpty()) {
                if (PlatformCatalog.isPlaylistLink(clean)) {
                    response.items.add(PlatformCatalog.loadPlaylist(clean));
                    return response;
                }
                JSONObject linkedTrack = PlatformCatalog.loadTrack(clean);
                String linkedQuery = firstNonEmpty(linkedTrack.optString("artist", ""), "")
                        + " " + linkedTrack.optString("title", "");
                SearchResponse matches = search(linkedQuery.trim(), selected, "track");
                JSONObject match = bestTrackMatch(matches.items, linkedTrack.optString("title", ""), linkedTrack.optString("artist", ""));
                if (match != null) response.items.add(match);
                else {
                    linkedTrack.put("preview_only", !linkedTrack.optString("preview_url", "").isEmpty());
                    response.items.add(linkedTrack);
                    response.errors.add("Трек распознан, но не найдено совпадение для воспроизведения. Открой ссылку площадки или попробуй поиск по названию.");
                }
                response.errors.addAll(matches.errors);
                return response;
            }
            response.items.add(resolveSearchLink(clean));
            return response;
        }

        ArrayList<String> sources = new ArrayList<>();
        if ("playlist".equals(searchKind)) {
            if ("all".equals(selected) || "youtube_music".equals(selected) || "youtube".equals(selected)) sources.add("youtube");
            if ("all".equals(selected) || "soundcloud".equals(selected)) sources.add("soundcloud");
        } else if ("all".equals(selected)) {
            sources.add("youtube_music"); sources.add("youtube"); sources.add("soundcloud");
        } else if ("youtube_music".equals(selected) || "youtube".equals(selected) || "soundcloud".equals(selected)) {
            sources.add(selected);
        } else if ("deezer".equals(selected) || "apple".equals(selected)) {
            sources.add(selected);
        } else {
            response.errors.add("Выбери доступный источник поиска.");
            return response;
        }

        CompletionService<ProviderSearch> completed = new ExecutorCompletionService<>(GLOBAL_SEARCH_POOL);
        ArrayList<Future<ProviderSearch>> futures = new ArrayList<>();
        for (String source : sources) {
            futures.add(completed.submit(() -> {
                try {
                    ArrayList<JSONObject> items = "playlist".equals(searchKind)
                            ? searchPlaylists(clean, source) : search(clean, source);
                    return new ProviderSearch(source, items, "");
                } catch (Exception error) {
                    String message = error.getMessage() == null ? "Источник не ответил" : error.getMessage();
                    return new ProviderSearch(source, new ArrayList<>(), message);
                }
            }));
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(22);
        ArrayList<ProviderSearch> providerResults = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            long remaining = deadline - System.nanoTime();
            Future<ProviderSearch> future = remaining > 0 ? completed.poll(remaining, TimeUnit.NANOSECONDS) : null;
            if (future == null) {
                for (Future<ProviderSearch> pending : futures) pending.cancel(true);
                response.errors.add("Источники отвечают слишком долго. Попробуй повторить поиск.");
                break;
            }
            ProviderSearch result = future.get();
            providerResults.add(result);
            if (!result.error.isEmpty()) response.errors.add(providerName(result.provider) + ": " + result.error);
        }

        providerResults.sort((a, b) -> Integer.compare(providerRank(a.provider), providerRank(b.provider)));
        Set<String> seen = new HashSet<>();
        int[] providerCounts = new int[5];
        for (ProviderSearch source : providerResults) {
            int rank = providerRank(source.provider);
            for (JSONObject item : source.items) {
                String id = item.optString("id", item.optString("url", ""));
                String family = ("youtube".equals(source.provider) || "youtube_music".equals(source.provider)) ? "youtube" : source.provider;
                if (id.isEmpty() || !seen.add(family + ":" + id.toLowerCase(Locale.ROOT))) continue;
                if ("all".equals(selected) && providerCounts[rank] >= 7) continue;
                response.items.add(item);
                providerCounts[rank]++;
                if (response.items.size() >= 20) return response;
            }
        }
        if ("track".equals(searchKind) && response.items.size() < 20 && ("all".equals(selected) || "deezer".equals(selected) || "apple".equals(selected))) {
            String[] previewSources = "apple".equals(selected) ? new String[]{"apple"}
                    : "deezer".equals(selected) ? new String[]{"deezer"} : new String[]{"deezer", "apple"};
            Set<String> seenPreview = new HashSet<>();
            for (JSONObject item : response.items) seenPreview.add(item.optString("url", "").toLowerCase(Locale.ROOT));
            for (String source : previewSources) {
                try {
                    int limit = Math.min(8, 20 - response.items.size());
                    for (JSONObject item : searchPreviewCatalog(clean, source, limit)) {
                        String url = item.optString("url", "").toLowerCase(Locale.ROOT);
                        if (!url.isEmpty() && seenPreview.add(url)) response.items.add(item);
                        if (response.items.size() >= 20) break;
                    }
                } catch (Exception error) {
                    response.errors.add(providerName(source) + ": " + (error.getMessage() == null ? "поиск недоступен" : error.getMessage()));
                }
                if (response.items.size() >= 20) break;
            }
            if (response.items.isEmpty() && response.errors.stream().anyMatch(error -> error.toLowerCase(Locale.ROOT).contains("sign in") || error.toLowerCase(Locale.ROOT).contains("authorization"))) {
                response.errors.add("YouTube просит подтвердить запрос. В результатах Apple Music и Deezer доступны превью; для полнотрекового поиска используй другой источник или публичную ссылку.");
            }
        }
        return response;
    }

    private static int providerRank(String provider) {
        if ("youtube_music".equals(provider)) return 0;
        if ("youtube".equals(provider)) return 1;
        if ("soundcloud".equals(provider)) return 2;
        if ("deezer".equals(provider)) return 3;
        return 4;
    }

    public static JSONObject findPlayableMatch(String title, String artist, int duration) throws Exception {
        String query = (artist == null ? "" : artist.trim()) + " " + (title == null ? "" : title.trim());
        SearchResponse response = search(query.trim(), "all", "track");
        JSONObject best = bestTrackMatch(response.items, title, artist);
        if (best == null) throw new IllegalStateException("Не удалось найти совпадение для воспроизведения. Проверь название и исполнителя.");
        if (duration > 0 && best.optInt("duration", 0) > 0 && Math.abs(duration - best.optInt("duration", 0)) > Math.max(12, duration / 3)) {
            JSONObject closeDuration = null;
            int closest = Integer.MAX_VALUE;
            for (JSONObject candidate : response.items) {
                int candidateDuration = candidate.optInt("duration", 0);
                if (candidateDuration <= 0) continue;
                int difference = Math.abs(duration - candidateDuration);
                if (difference < closest) { closest = difference; closeDuration = candidate; }
            }
            if (closeDuration != null && closest < Math.abs(duration - best.optInt("duration", 0))) best = closeDuration;
        }
        return best;
    }

    public static JSONObject fetchLyrics(String title, String artist, String album, int duration) throws Exception {
        String cleanTitle = title == null ? "" : title.trim();
        String cleanArtist = artist == null ? "" : artist.trim();
        if (cleanTitle.isEmpty()) throw new IllegalArgumentException("У трека нет названия для поиска текста.");
        StringBuilder address = new StringBuilder("https://lrclib.net/api/get?track_name=")
                .append(URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8.name()));
        if (!cleanArtist.isEmpty() && !"неизвестный исполнитель".equalsIgnoreCase(cleanArtist)) {
            address.append("&artist_name=").append(URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8.name()));
        }
        if (album != null && !album.trim().isEmpty()) address.append("&album_name=").append(URLEncoder.encode(album.trim(), StandardCharsets.UTF_8.name()));
        if (duration > 0) address.append("&duration=").append(duration);
        HttpURLConnection connection = (HttpURLConnection) new URL(address.toString()).openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(14000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "LAST-FREQUENCY/1.4 Android music player");
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) throw new IllegalStateException("Текст песни не найден.");
            throw new IllegalStateException("Сервис текстов ответил HTTP " + status + ".");
        }
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && response.length() < 1_000_000) response.append(line);
        } finally { connection.disconnect(); }
        return new JSONObject(response.toString());
    }

    private static JSONObject bestTrackMatch(ArrayList<JSONObject> candidates, String title, String artist) {
        String targetTitle = normalizedMusicText(title), targetArtist = normalizedMusicText(artist);
        JSONObject best = null; double bestScore = 0;
        for (JSONObject candidate : candidates) {
            if (candidate.optBoolean("is_playlist", false) || "playlist".equals(candidate.optString("kind", ""))) continue;
            String candidateTitle = normalizedMusicText(candidate.optString("title", ""));
            String candidateArtist = normalizedMusicText(candidate.optString("artist", ""));
            double titleScore = similarity(targetTitle, candidateTitle);
            double artistScore = targetArtist.isEmpty() || candidateArtist.isEmpty() ? 0.35 : similarity(targetArtist, candidateArtist);
            double score = titleScore * 0.76 + artistScore * 0.24;
            if (candidateTitle.contains(targetTitle) || targetTitle.contains(candidateTitle)) score += 0.1;
            if (candidate.optBoolean("preview_only", false) && candidate.optString("preview_url", "").isEmpty()) score -= 0.2;
            if (score > bestScore) { bestScore = score; best = candidate; }
        }
        return bestScore >= 0.48 ? best : null;
    }

    private static String normalizedMusicText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\b(official|audio|video|lyrics?|visuali[sz]er|remaster(?:ed)?|topic|hd|4k)\\b", " ").replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static double similarity(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        if (left.equals(right)) return 1;
        Set<String> a = new HashSet<>(); Collections.addAll(a, left.split(" "));
        Set<String> b = new HashSet<>(); Collections.addAll(b, right.split(" "));
        Set<String> overlap = new HashSet<>(a); overlap.retainAll(b);
        return (2d * overlap.size()) / Math.max(1, a.size() + b.size());
    }

    private static ArrayList<JSONObject> searchPlaylists(String query, String provider) throws Exception {
        ArrayList<JSONObject> results = new ArrayList<>();
        if ("soundcloud".equals(provider)) {
            throw new IllegalStateException("Поиск плейлистов SoundCloud без авторизации недоступен. Можно вставить прямую ссылку на плейлист.");
        }
        String target = "https://www.youtube.com/results?search_query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8.name()) + "&sp=EgIQAw%3D%3D";
        JSONObject root = dumpJson(target, 20, false);
        JSONArray entries = root.optJSONArray("entries");
        if (entries == null) return results;
        for (int i = 0; i < entries.length() && results.size() < 20; i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            JSONObject playlist = toPlaylistResult(entry, "youtube");
            if (playlist != null) results.add(playlist);
        }
        return results;
    }

    private static JSONObject resolveSearchLink(String url) throws Exception {
        String lower = url.toLowerCase(Locale.ROOT);
        String provider = lower.contains("soundcloud.com") ? "soundcloud" : "youtube";
        if (!(lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("soundcloud.com"))) {
            throw new IllegalArgumentException("Вставь ссылку YouTube или SoundCloud либо найди плейлист на поддерживаемой площадке.");
        }
        boolean singleVideo = lower.contains("watch?v=") || lower.contains("youtu.be/");
        JSONObject root = dumpJson(url, 100, singleVideo);
        JSONArray entries = root.optJSONArray("entries");
        if (entries != null && !singleVideo) {
            JSONObject playlist = toPlaylistResult(root, provider, url);
            if (playlist != null) return playlist;
        }
        JSONObject track = toResult(root, provider);
        if (track == null) throw new IllegalArgumentException("Не удалось распознать трек по этой ссылке.");
        return track;
    }

    private static JSONObject dumpJson(String url, int playlistEnd, boolean noPlaylist) throws Exception {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--dump-single-json");
        request.addOption("--flat-playlist");
        request.addOption("--skip-download");
        request.addOption("--no-warnings");
        request.addOption("--ignore-errors");
        request.addOption("--playlist-end", String.valueOf(playlistEnd));
        request.addOption("--socket-timeout", "18");
        if (noPlaylist) request.addOption("--no-playlist");
        String output = YoutubeDL.getInstance().execute(request).getOut();
        if (output == null || output.trim().isEmpty()) throw new IllegalStateException("Источник не вернул результаты.");
        return new JSONObject(output.trim());
    }

    public static JSONObject loadPlaylist(String url) throws Exception {
        if (!PlatformCatalog.providerForUrl(url).isEmpty()) return PlatformCatalog.loadPlaylist(url);
        JSONObject source = dumpJson(url, 100, false);
        JSONArray entries = source.optJSONArray("entries");
        if (entries == null) throw new IllegalStateException("Не удалось загрузить список треков этого плейлиста.");
        String provider = url.toLowerCase(Locale.ROOT).contains("soundcloud.com") ? "soundcloud" : "youtube";
        JSONArray tracks = new JSONArray();
        for (int i = 0; i < entries.length() && tracks.length() < 100; i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            JSONObject track = toResult(entry, provider);
            if (track != null) tracks.put(track);
        }
        JSONObject result = new JSONObject();
        result.put("title", source.optString("title", "Плейлист"));
        result.put("track_count", source.optInt("playlist_count", tracks.length()));
        result.put("tracks", tracks);
        return result;
    }

    private static JSONObject toPlaylistResult(JSONObject entry, String provider) {
        return toPlaylistResult(entry, provider, "");
    }

    private static JSONObject toPlaylistResult(JSONObject entry, String provider, String fallbackUrl) {
        String id = entry.optString("id", "");
        String title = entry.optString("title", "Плейлист").trim();
        String url = firstNonEmpty(entry.optString("webpage_url", ""), entry.optString("original_url", ""), entry.optString("url", ""), fallbackUrl);
        if (url.isEmpty() && !id.isEmpty() && "youtube".equals(provider)) url = "https://www.youtube.com/playlist?list=" + id;
        String type = entry.optString("_type", "");
        boolean looksLikePlaylist = "playlist".equals(type) || "multi_video".equals(type)
                || id.startsWith("PL") || id.startsWith("UU") || id.startsWith("LL") || id.startsWith("FL")
                || id.startsWith("RD") || id.startsWith("OLAK5uy");
        if (!looksLikePlaylist || title.isEmpty() || url.isEmpty()) return null;
        JSONObject result = new JSONObject();
        try {
            result.put("id", id.isEmpty() ? url : id);
            result.put("title", title);
            result.put("artist", firstNonEmpty(entry.optString("uploader", ""), entry.optString("channel", ""), providerName(provider)));
            result.put("track_count", entry.optInt("playlist_count", entry.optInt("entries", 0)));
            result.put("thumbnail", entry.optString("thumbnail", ""));
            result.put("url", url);
            result.put("provider", provider);
            result.put("kind", "playlist");
            result.put("is_playlist", true);
        } catch (Exception ignored) { }
        return result;
    }

    private static ArrayList<JSONObject> searchPreviewCatalog(String query, String provider, int limit) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        String address = "deezer".equals(provider)
                ? "https://api.deezer.com/search?q=" + encoded + "&limit=" + limit
                : "https://itunes.apple.com/search?term=" + encoded + "&entity=song&media=music&limit=" + limit;
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Last Frequency Android");
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && response.length() < 3_000_000) response.append(line);
        } finally { connection.disconnect(); }
        JSONObject root = new JSONObject(response.toString());
        JSONArray items = "deezer".equals(provider) ? root.optJSONArray("data") : root.optJSONArray("results");
        ArrayList<JSONObject> results = new ArrayList<>();
        if (items == null) return results;
        for (int i = 0; i < items.length() && results.size() < limit; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String title, artist, url, thumbnail, preview;
            int duration;
            if ("deezer".equals(provider)) {
                JSONObject artistData = item.optJSONObject("artist"), albumData = item.optJSONObject("album");
                title = item.optString("title", "");
                artist = artistData == null ? "" : artistData.optString("name", "");
                url = item.optString("link", "");
                thumbnail = albumData == null ? "" : albumData.optString("cover_medium", "");
                preview = item.optString("preview", "");
                duration = item.optInt("duration", 0);
            } else {
                title = item.optString("trackName", "");
                artist = item.optString("artistName", "");
                url = item.optString("trackViewUrl", "");
                thumbnail = item.optString("artworkUrl100", "").replace("100x100bb", "512x512bb");
                preview = item.optString("previewUrl", "");
                duration = item.optInt("trackTimeMillis", 0) / 1000;
            }
            if (title.trim().isEmpty() || url.isEmpty()) continue;
            JSONObject result = new JSONObject();
            result.put("id", url);
            result.put("title", title);
            result.put("artist", artist.isEmpty() ? "Неизвестный автор" : artist);
            result.put("duration", duration);
            result.put("url", url);
            result.put("thumbnail", thumbnail);
            result.put("provider", provider);
            result.put("source", providerName(provider));
            result.put("preview_url", preview);
            result.put("preview_only", true);
            results.add(result);
        }
        return results;
    }

    private static JSONObject toResult(JSONObject entry, String provider) {
        String id = entry.optString("id", "");
        String title = entry.optString("title", "Без названия").trim();
        if (title.isEmpty() || "null".equals(title)) return null;
        if ("youtube".equals(provider) && NON_MUSIC.matcher(title).find()) return null;
        int duration = entry.optInt("duration", 0);
        if ("youtube".equals(provider) && duration > 900) return null;
        String url = entry.optString("webpage_url", "");
        if (url.isEmpty()) url = entry.optString("permalink_url", "");
        if (url.isEmpty()) url = entry.optString("url", "");
        if (("youtube".equals(provider) || "youtube_music".equals(provider)) && !id.isEmpty()
                && !url.startsWith("http")) url = "https://www.youtube.com/watch?v=" + id;
        if (url.isEmpty()) return null;
        String artist = firstNonEmpty(metadataText(entry, "artist"), artistArray(entry.optJSONArray("artists")),
                artistArray(entry.optJSONArray("artist_metadata")), metadataText(entry, "uploader"),
                metadataText(entry, "channel"), metadataText(entry, "uploader_id"), "Неизвестный автор");
        String album = firstNonEmpty(metadataText(entry, "album"), metadataText(entry, "playlist_title"));
        String thumbnail = entry.optString("thumbnail", "");
        if (thumbnail.isEmpty() && "youtube".equals(provider) && id.matches("[A-Za-z0-9_-]{11}")) {
            thumbnail = "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
        }
        JSONObject result = new JSONObject();
        try {
            result.put("id", id);
            result.put("title", title);
            result.put("artist", artist);
            result.put("album", album);
            result.put("duration", duration);
            result.put("url", url);
            result.put("thumbnail", thumbnail);
            result.put("provider", provider);
            result.put("source", providerName(provider));
        } catch (Exception ignored) { }
        return result;
    }

    private static String metadataText(JSONObject object, String key) {
        Object value = object == null ? null : object.opt(key);
        if (value instanceof String) return ((String) value).trim();
        if (value instanceof JSONObject) {
            JSONObject metadata = (JSONObject) value;
            return firstNonEmpty(metadata.optString("name", ""), metadata.optString("title", ""), metadata.optString("artist", ""));
        }
        if (value instanceof JSONArray) return artistArray((JSONArray) value);
        return "";
    }

    private static String artistArray(JSONArray values) {
        if (values == null) return "";
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            Object value = values.opt(i);
            String name = value instanceof JSONObject ? metadataText((JSONObject) value, "name")
                    : value == null ? "" : String.valueOf(value).trim();
            if (!name.isEmpty() && !names.contains(name)) names.add(name);
        }
        return android.text.TextUtils.join(", ", names);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty() && !"null".equals(value)) return value.trim();
        return "";
    }

    public static String providerName(String provider) {
        if ("youtube_music".equals(provider)) return "YouTube Music";
        if ("youtube".equals(provider)) return "YouTube";
        if ("soundcloud".equals(provider)) return "SoundCloud";
        if ("deezer".equals(provider)) return "Deezer";
        if ("apple".equals(provider)) return "Apple Music";
        if ("yandex".equals(provider)) return "Яндекс Музыка";
        return "Интернет-поиск";
    }

    public static JSONObject resolve(String pageUrl) throws Exception {
        YoutubeDLRequest request = new YoutubeDLRequest(pageUrl);
        request.addOption("-f", "bestaudio/best");
        request.addOption("--no-playlist");
        request.addOption("--no-warnings");
        request.addOption("--socket-timeout", "20");
        VideoInfo info = YoutubeDL.getInstance().getInfo(request);
        String streamUrl = info.getUrl();
        if (streamUrl == null || !streamUrl.startsWith("https://")) {
            throw new IllegalStateException("Источник не предоставил совместимый HTTPS-поток");
        }
        JSONObject result = new JSONObject();
        result.put("url", streamUrl);
        result.put("title", info.getTitle());
        result.put("artist", firstNonEmpty(info.getUploader(), info.getUploaderId(), "Неизвестный автор"));
        result.put("duration", info.getDuration());
        result.put("thumbnail", info.getThumbnail() == null ? "" : info.getThumbnail());
        JSONObject headers = new JSONObject();
        if (info.getHttpHeaders() != null) for (String key : info.getHttpHeaders().keySet()) {
            String value = info.getHttpHeaders().get(key);
            if (key != null && value != null && ("user-agent".equalsIgnoreCase(key)
                    || "referer".equalsIgnoreCase(key) || "accept".equalsIgnoreCase(key))) headers.put(key, value);
        }
        result.put("headers", headers);
        return result;
    }

    public static File download(Context context, String pageUrl, ProgressListener progress) throws Exception {
        File music = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File destination = new File(music == null ? context.getFilesDir() : music, "Last Frequency");
        if (!destination.exists() && !destination.mkdirs()) throw new IllegalStateException("Не удалось создать папку загрузок");
        long startedAt = System.currentTimeMillis();
        YoutubeDLRequest request = new YoutubeDLRequest(pageUrl);
        request.addOption("-f", "bestaudio/best");
        request.addOption("--extract-audio");
        request.addOption("--audio-format", "mp3");
        request.addOption("--audio-quality", "192K");
        request.addOption("--embed-metadata");
        request.addOption("--embed-thumbnail");
        request.addOption("--no-playlist");
        request.addOption("--no-warnings");
        request.addOption("--socket-timeout", "30");
        request.addOption("-o", new File(destination, "%(title).180B [%(id)s].%(ext)s").getAbsolutePath());
        String processId = "download-" + UUID.randomUUID();
        Function3<Float, Long, String, Unit> callback = (percent, eta, line) -> {
            if (progress != null && percent != null) progress.onProgress(percent);
            return Unit.INSTANCE;
        };
        YoutubeDL.getInstance().execute(request, processId, false, callback);
        File[] files = destination.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".mp3"));
        File latest = null;
        if (files != null) for (File file : files) if (file.lastModified() >= startedAt - 1000
                && (latest == null || file.lastModified() > latest.lastModified())) latest = file;
        if (latest == null) throw new IllegalStateException("Файл MP3 не появился. Источник мог запретить скачивание или потребовать авторизацию.");
        return latest;
    }
}
