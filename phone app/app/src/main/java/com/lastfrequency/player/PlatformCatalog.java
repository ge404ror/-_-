package com.lastfrequency.player;

import android.net.Uri;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Public metadata importers for Spotify, Apple Music, and Yandex Music links. */
public final class PlatformCatalog {
    private static final int MAX_RESPONSE_BYTES = 8_000_000;
    private static final Pattern SPOTIFY_PATH = Pattern.compile("/(?:intl-[a-z]{2}/)?(?:embed/)?(playlist|track|album)/([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern YANDEX_USER_PLAYLIST = Pattern.compile("/users/([^/]+)/playlists/([^/]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern YANDEX_PLAYLIST = Pattern.compile("/playlists/([^/]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern YANDEX_SHORT_PLAYLIST = Pattern.compile("/playlist/([^/]+)(?:/([^/]+))?", Pattern.CASE_INSENSITIVE);

    private PlatformCatalog() { }

    public static String providerForUrl(String value) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.matches("(?i)spotify:(?:playlist|track|album):[A-Za-z0-9]+")) return "spotify";
        Uri uri = Uri.parse(clean);
        String host = uri.getHost();
        if (host == null || !"https".equalsIgnoreCase(uri.getScheme())) return "";
        host = host.toLowerCase(Locale.ROOT);
        if (host.equals("open.spotify.com") || host.equals("www.open.spotify.com") || host.equals("spotify.link")) return "spotify";
        if (host.equals("music.apple.com")) return "apple";
        if (host.startsWith("music.yandex.")) return "yandex";
        return "";
    }

    public static boolean isPlaylistLink(String value) {
        String provider = providerForUrl(value);
        if (provider.isEmpty()) return false;
        if ("spotify".equals(provider)) return spotifyType(value).equals("playlist") || spotifyType(value).equals("album");
        if ("apple".equals(provider)) {
            Uri uri = Uri.parse(value);
            if (uri.getQueryParameter("i") != null) return false;
            String path = uri.getPath() == null ? "" : uri.getPath();
            return path.matches("(?i).*/(?:playlist|album)/.*");
        }
        String path = Uri.parse(value).getPath();
        return path != null && (YANDEX_USER_PLAYLIST.matcher(path).find() || YANDEX_PLAYLIST.matcher(path).find()
                || YANDEX_SHORT_PLAYLIST.matcher(path).find() || path.matches("(?i).*/album/[^/]+/?"));
    }

    public static JSONObject loadPlaylist(String value) throws Exception {
        String provider = providerForUrl(value);
        if (provider.isEmpty() || !isPlaylistLink(value)) throw new IllegalArgumentException("Вставь публичную ссылку на плейлист или альбом Spotify, Apple Music или Яндекс Музыки.");
        if ("spotify".equals(provider)) return loadSpotifyList(value);
        if ("apple".equals(provider)) return loadAppleList(value);
        return loadYandexList(value);
    }

    public static JSONObject loadTrack(String value) throws Exception {
        String provider = providerForUrl(value);
        if (provider.isEmpty() || isPlaylistLink(value)) throw new IllegalArgumentException("Это не ссылка на отдельный трек.");
        if ("spotify".equals(provider)) return loadSpotifyTrack(value);
        if ("apple".equals(provider)) return loadAppleTrack(value);
        return loadYandexTrack(value);
    }

    private static JSONObject loadSpotifyList(String value) throws Exception {
        String id = spotifyId(value);
        String type = spotifyType(value);
        if (id.isEmpty() || !("playlist".equals(type) || "album".equals(type))) throw new IllegalArgumentException("Не удалось распознать плейлист Spotify.");
        JSONObject entity = spotifyEntity(type, id);
        JSONArray sourceTracks = entity.optJSONArray("trackList");
        if (sourceTracks == null) sourceTracks = entity.optJSONArray("tracks");
        JSONArray tracks = new JSONArray();
        if (sourceTracks != null) {
            for (int i = 0; i < sourceTracks.length() && tracks.length() < 500; i++) {
                JSONObject item = sourceTracks.optJSONObject(i);
                if (item == null) continue;
                String title = first(item.optString("title", ""), item.optString("name", ""));
                String artist = first(item.optString("subtitle", ""), item.optString("artist", ""));
                if (title.isEmpty()) continue;
                JSONObject track = new JSONObject();
                track.put("id", item.optString("uri", ""));
                track.put("title", title);
                track.put("artist", artist);
                track.put("duration", millisOrSeconds(item.optLong("duration", 0)));
                track.put("thumbnail", first(item.optString("image", ""), spotifyCover(entity)));
                track.put("preview_url", item.optJSONObject("audioPreview") == null ? "" : item.optJSONObject("audioPreview").optString("url", ""));
                track.put("url", "https://open.spotify.com/track/" + spotifyTrackId(item));
                track.put("provider", "spotify");
                tracks.put(track);
            }
        }
        if (tracks.length() == 0) throw new IllegalStateException("Spotify не вернул треки. Проверь, что плейлист публичный.");
        JSONObject result = new JSONObject();
        result.put("id", id); result.put("title", first(entity.optString("name", ""), "Spotify"));
        result.put("artist", "Spotify"); result.put("thumbnail", spotifyCover(entity)); result.put("url", value);
        result.put("provider", "spotify"); result.put("kind", "playlist"); result.put("is_playlist", true);
        result.put("track_count", entity.optInt("totalCount", tracks.length())); result.put("tracks", tracks);
        return result;
    }

    private static JSONObject loadSpotifyTrack(String value) throws Exception {
        String id = spotifyId(value);
        String type = spotifyType(value);
        if (id.isEmpty() || !"track".equals(type)) throw new IllegalArgumentException("Вставь ссылку Spotify на трек.");
        JSONObject entity = spotifyEntity(type, id);
        JSONArray list = entity.optJSONArray("trackList");
        JSONObject item = list == null || list.length() == 0 ? entity : list.optJSONObject(0);
        if (item == null) item = entity;
        JSONObject result = new JSONObject();
        result.put("id", id); result.put("title", first(item.optString("title", ""), item.optString("name", ""), entity.optString("name", "")));
        result.put("artist", first(item.optString("subtitle", ""), item.optString("artist", "")));
        result.put("duration", millisOrSeconds(item.optLong("duration", 0)));
        result.put("thumbnail", first(item.optString("image", ""), spotifyCover(entity)));
        result.put("preview_url", item.optJSONObject("audioPreview") == null ? "" : item.optJSONObject("audioPreview").optString("url", ""));
        result.put("url", "https://open.spotify.com/track/" + id); result.put("provider", "spotify");
        return result;
    }

    private static JSONObject spotifyEntity(String type, String id) throws Exception {
        String page = getText("https://open.spotify.com/embed/" + type + "/" + id,
                "open.spotify.com", "open.spotify.com", "spotify.link");
        Matcher script = Pattern.compile("<script[^>]+id=[\"']__NEXT_DATA__[\"'][^>]*>([\\s\\S]*?)</script>", Pattern.CASE_INSENSITIVE).matcher(page);
        if (!script.find()) throw new IllegalStateException("Spotify не отдал открытые данные. Проверь, что ссылка публичная.");
        JSONObject root = new JSONObject(htmlDecode(script.group(1)));
        JSONObject props = root.optJSONObject("props");
        JSONObject pageProps = props == null ? null : props.optJSONObject("pageProps");
        JSONObject state = pageProps == null ? null : pageProps.optJSONObject("state");
        JSONObject data = state == null ? null : state.optJSONObject("data");
        JSONObject entity = data == null ? null : data.optJSONObject("entity");
        if (entity == null) throw new IllegalStateException("Spotify не вернул карточку трека или плейлиста.");
        return entity;
    }

    private static JSONObject loadAppleList(String value) throws Exception {
        Uri uri = Uri.parse(value);
        String path = uri.getPath() == null ? "" : uri.getPath();
        Matcher album = Pattern.compile("(?i)/album/(?:[^/]+/)?(\\d+)(?:/|$)").matcher(path);
        if (album.find()) {
            JSONObject lookup = itunesLookup(album.group(1));
            JSONArray raw = lookup.optJSONArray("results");
            JSONObject collection = null; JSONArray tracks = new JSONArray();
            if (raw != null) for (int i = 0; i < raw.length(); i++) {
                JSONObject item = raw.optJSONObject(i); if (item == null) continue;
                if ("collection".equals(item.optString("wrapperType"))) collection = item;
                else if ("track".equals(item.optString("wrapperType"))) tracks.put(appleTrack(item, collection));
            }
            if (tracks.length() == 0) throw new IllegalStateException("Apple Music не вернул песни этого альбома.");
            JSONObject result = new JSONObject();
            result.put("id", album.group(1)); result.put("title", collection == null ? "Apple Music" : collection.optString("collectionName", "Apple Music"));
            result.put("artist", "Apple Music"); result.put("thumbnail", collection == null ? "" : appleArtwork(collection.optString("artworkUrl100", "")));
            result.put("url", value); result.put("provider", "apple"); result.put("kind", "playlist"); result.put("is_playlist", true);
            result.put("track_count", tracks.length()); result.put("tracks", tracks); return result;
        }
        String page = getText(value, "music.apple.com");
        JSONObject data = findJsonScript(page, "serialized-server-data");
        if (data == null) data = findJsonScript(page, "schema:music-playlist");
        if (data == null) throw new IllegalStateException("Apple Music не отдал список треков. Плейлист должен быть публичным.");
        ArrayList<JSONObject> found = new ArrayList<>();
        String[] titleCover = new String[]{"Apple Music", ""};
        walkApple(data, found, titleCover, 0);
        JSONArray tracks = new JSONArray(); for (JSONObject item : found) tracks.put(item);
        if (tracks.length() == 0) throw new IllegalStateException("В публичных данных Apple Music не найдено треков.");
        JSONObject result = new JSONObject();
        result.put("id", value); result.put("title", titleCover[0]); result.put("artist", "Apple Music"); result.put("thumbnail", titleCover[1]);
        result.put("url", value); result.put("provider", "apple"); result.put("kind", "playlist"); result.put("is_playlist", true);
        result.put("track_count", tracks.length()); result.put("tracks", tracks); return result;
    }

    private static JSONObject loadAppleTrack(String value) throws Exception {
        Uri uri = Uri.parse(value);
        String id = uri.getQueryParameter("i");
        if (id == null || id.isEmpty()) {
            Matcher matcher = Pattern.compile("/(?:[a-z]{2}/)?(?:album|song)/[^/]+/(\\d+)(?:/|$)", Pattern.CASE_INSENSITIVE).matcher(uri.getPath() == null ? "" : uri.getPath());
            if (matcher.find()) id = matcher.group(1);
        }
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("Не удалось распознать номер трека Apple Music.");
        JSONObject root = itunesLookup(id); JSONArray items = root.optJSONArray("results");
        if (items == null || items.length() == 0) throw new IllegalStateException("Apple Music не нашёл этот трек.");
        JSONObject item = items.optJSONObject(0); JSONObject result = appleTrack(item, null); result.put("url", value); return result;
    }

    private static JSONObject itunesLookup(String id) throws Exception {
        return getJson("https://itunes.apple.com/lookup?id=" + URLEncoder.encode(id, "UTF-8") + "&entity=song&limit=1000", "itunes.apple.com");
    }

    private static JSONObject appleTrack(JSONObject item, JSONObject collection) throws Exception {
        JSONObject result = new JSONObject();
        String title = item.optString("trackName", "Без названия");
        String artist = first(item.optString("artistName", ""), collection == null ? "" : collection.optString("artistName", ""));
        result.put("id", item.optString("trackId", "")); result.put("title", title); result.put("artist", artist);
        result.put("album", first(item.optString("collectionName", ""), collection == null ? "" : collection.optString("collectionName", "")));
        result.put("duration", Math.max(0, item.optLong("trackTimeMillis", 0) / 1000));
        result.put("thumbnail", appleArtwork(item.optString("artworkUrl100", collection == null ? "" : collection.optString("artworkUrl100", ""))));
        result.put("preview_url", item.optString("previewUrl", ""));
        result.put("url", item.optString("trackViewUrl", "")); result.put("provider", "apple"); return result;
    }

    private static void walkApple(Object node, ArrayList<JSONObject> tracks, String[] meta, int depth) throws Exception {
        if (depth > 24 || tracks.size() >= 500) return;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node; for (int i = 0; i < array.length(); i++) walkApple(array.opt(i), tracks, meta, depth + 1); return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject object = (JSONObject) node;
        JSONObject attrs = object.optJSONObject("attributes"); if (attrs == null) attrs = object;
        String type = object.optString("type", "").toLowerCase(Locale.ROOT);
        String descriptor = object.optJSONObject("contentDescriptor") == null ? "" : object.optJSONObject("contentDescriptor").optString("kind", "");
        String title = first(attrs.optString("name", ""), object.optString("title", ""));
        String artist = first(attrs.optString("artistName", ""), object.optString("artistName", ""));
        Object artistNode = attrs.opt("artist");
        if (artist.isEmpty() && artistNode instanceof JSONObject) artist = ((JSONObject) artistNode).optString("name", "");
        else if (artist.isEmpty() && artistNode instanceof String) artist = (String) artistNode;
        boolean isTrack = "songs".equals(type) || "song".equals(type) || "musicrecording".equals(object.optString("@type", "").toLowerCase(Locale.ROOT))
                || "song".equals(descriptor) || attrs.has("audioTraits");
        if (title.isEmpty()) title = first(object.optString("name", ""));
        if (isTrack && !title.isEmpty() && !artist.isEmpty()) {
            JSONObject track = new JSONObject(); track.put("title", title); track.put("artist", artist);
            track.put("duration", millisOrSeconds(attrs.optLong("durationInMillis", attrs.optLong("duration", 0))));
            JSONObject artwork = attrs.optJSONObject("artwork");
            String cover = artwork == null ? "" : artwork.optString("url", "");
            if (!cover.isEmpty()) cover = cover.replace("{w}x{h}bb.{f}", "600x600bb.jpg");
            track.put("thumbnail", cover);
            track.put("url", "https://music.apple.com/search?term=" + enc(artist + " " + title)); track.put("provider", "apple");
            tracks.add(track); return;
        }
        if (meta[0].equals("Apple Music") && ("playlists".equals(type) || "playlist".equals(descriptor) || "MusicPlaylist".equals(object.optString("@type", "")))) {
            meta[0] = first(attrs.optString("name", ""), object.optString("title", "Apple Music"));
        }
        if (meta[1].isEmpty()) {
            JSONObject artwork = attrs.optJSONObject("artwork");
            if (artwork != null) meta[1] = artwork.optString("url", "").replace("{w}x{h}bb.{f}", "600x600bb.jpg");
        }
        for (java.util.Iterator<String> it = object.keys(); it.hasNext();) walkApple(object.opt(it.next()), tracks, meta, depth + 1);
    }

    private static JSONObject loadYandexList(String value) throws Exception {
        Uri uri = Uri.parse(value); String path = uri.getPath() == null ? "" : uri.getPath();
        Matcher user = YANDEX_USER_PLAYLIST.matcher(path); Matcher direct = YANDEX_SHORT_PLAYLIST.matcher(path); Matcher playlist = YANDEX_PLAYLIST.matcher(path);
        String apiPath;
        boolean hasUser = user.find();
        boolean hasDirect = direct.find();
        boolean hasPlaylist = playlist.find();
        if (hasUser) apiPath = "/users/" + enc(user.group(1)) + "/playlists/" + enc(user.group(2));
        else if (hasDirect && direct.group(2) != null) apiPath = "/users/" + enc(direct.group(1)) + "/playlists/" + enc(direct.group(2));
        else if (hasDirect) apiPath = "/playlist/" + enc(direct.group(1));
        else if (hasPlaylist) apiPath = "/playlist/" + enc(playlist.group(1));
        else if (path.matches("(?i).*/album/[^/]+/?")) {
            Matcher album = Pattern.compile("(?i)/album/([^/]+)").matcher(path); album.find();
            JSONObject result = getJson("https://api.music.yandex.net/albums/" + enc(album.group(1)) + "/with-tracks", "api.music.yandex.net");
            return yandexAlbumResult(result, value);
        } else throw new IllegalArgumentException("Не удалось распознать ссылку на плейлист Яндекс Музыки.");
        JSONObject root = getJson("https://api.music.yandex.net" + apiPath, "api.music.yandex.net");
        JSONObject result = root.optJSONObject("result"); if (result == null) result = root;
        JSONArray rawTracks = result.optJSONArray("tracks"); JSONArray tracks = new JSONArray();
        if (rawTracks != null) for (int i = 0; i < rawTracks.length() && tracks.length() < 500; i++) {
            JSONObject entry = rawTracks.optJSONObject(i); if (entry == null) continue;
            JSONObject item = entry.optJSONObject("track"); if (item == null) item = entry;
            JSONObject converted = yandexTrack(item); if (converted != null) tracks.put(converted);
        }
        if (tracks.length() == 0) throw new IllegalStateException("Плейлист Яндекс Музыки пустой, закрытый или недоступен.");
        JSONObject output = new JSONObject(); output.put("id", first(result.optString("uid", ""), result.optString("kind", "")));
        output.put("title", first(result.optString("title", ""), result.optString("name", "Яндекс Музыка")));
        output.put("artist", "Яндекс Музыка"); output.put("thumbnail", yandexCover(result)); output.put("url", value);
        output.put("provider", "yandex"); output.put("kind", "playlist"); output.put("is_playlist", true);
        output.put("track_count", result.optInt("trackCount", tracks.length())); output.put("tracks", tracks); return output;
    }

    private static JSONObject yandexAlbumResult(JSONObject root, String value) throws Exception {
        JSONObject result = root.optJSONObject("result"); if (result == null) result = root;
        JSONArray volumes = result.optJSONArray("volumes"); JSONArray tracks = new JSONArray();
        if (volumes != null) for (int i = 0; i < volumes.length(); i++) {
            JSONArray volume = volumes.optJSONArray(i); if (volume == null) continue;
            for (int j = 0; j < volume.length(); j++) { JSONObject item = volume.optJSONObject(j); JSONObject converted = item == null ? null : yandexTrack(item); if (converted != null) tracks.put(converted); }
        }
        if (tracks.length() == 0) throw new IllegalStateException("Яндекс Музыка не вернула песни этого альбома.");
        JSONObject output = new JSONObject(); output.put("id", result.optString("id", "")); output.put("title", result.optString("title", "Яндекс Музыка"));
        output.put("artist", "Яндекс Музыка"); output.put("thumbnail", yandexCover(result)); output.put("url", value);
        output.put("provider", "yandex"); output.put("kind", "playlist"); output.put("is_playlist", true); output.put("track_count", tracks.length()); output.put("tracks", tracks); return output;
    }

    private static JSONObject loadYandexTrack(String value) throws Exception {
        String path = Uri.parse(value).getPath() == null ? "" : Uri.parse(value).getPath();
        Matcher matcher = Pattern.compile("(?i)/track/([^/]+)").matcher(path);
        if (!matcher.find()) throw new IllegalArgumentException("Не удалось распознать ссылку на трек Яндекс Музыки.");
        JSONObject root = getJson("https://api.music.yandex.net/tracks/" + enc(matcher.group(1)), "api.music.yandex.net");
        Object raw = root.opt("result"); JSONObject item = raw instanceof JSONArray ? ((JSONArray) raw).optJSONObject(0) : raw instanceof JSONObject ? (JSONObject) raw : null;
        JSONObject result = item == null ? null : yandexTrack(item); if (result == null) throw new IllegalStateException("Яндекс Музыка не нашла этот трек.");
        result.put("url", value); return result;
    }

    private static JSONObject yandexTrack(JSONObject item) throws Exception {
        String title = item.optString("title", ""); if (title.isEmpty()) return null;
        JSONArray artists = item.optJSONArray("artists"); ArrayList<String> names = new ArrayList<>();
        if (artists != null) for (int i = 0; i < artists.length(); i++) { JSONObject artist = artists.optJSONObject(i); if (artist != null && !artist.optString("name", "").isEmpty()) names.add(artist.optString("name")); }
        JSONObject result = new JSONObject(); result.put("id", item.optString("id", "")); result.put("title", title);
        result.put("artist", android.text.TextUtils.join(", ", names)); result.put("duration", Math.max(0, item.optLong("durationMs", 0) / 1000));
        String cover = item.optString("coverUri", ""); result.put("thumbnail", cover.isEmpty() ? "" : cover.startsWith("https://") ? cover : "https://" + cover.replace("%%", "400x400"));
        result.put("url", "https://music.yandex.ru/search?text=" + enc(result.optString("artist", "") + " " + title));
        result.put("provider", "yandex"); return result;
    }

    private static String yandexCover(JSONObject result) {
        String value = result.optString("ogImage", ""); if (value.isEmpty()) {
            Object cover = result.opt("cover"); value = cover instanceof JSONObject ? ((JSONObject) cover).optString("uri", "") : cover instanceof String ? (String) cover : "";
        }
        return value.isEmpty() ? "" : value.startsWith("https://") ? value : "https://" + value.replace("%%", "400x400");
    }

    private static JSONObject findJsonScript(String page, String id) {
        Matcher matcher = Pattern.compile("<script\\b(?=[^>]*\\bid=[\"']" + Pattern.quote(id) + "[\"'])[^>]*>([\\s\\S]*?)</script>", Pattern.CASE_INSENSITIVE).matcher(page);
        if (!matcher.find()) return null;
        try { return new JSONObject(htmlDecode(matcher.group(1))); } catch (Exception ignored) { return null; }
    }

    private static JSONObject getJson(String address, String allowedHost) throws Exception { return new JSONObject(getText(address, allowedHost)); }

    private static String getText(String address, String... allowedHosts) throws Exception {
        URL current = new URL(address);
        for (int redirects = 0; redirects <= 4; redirects++) {
            if (!"https".equalsIgnoreCase(current.getProtocol()) || !hostAllowed(current.getHost(), allowedHosts)) throw new IllegalArgumentException("Ссылка перенаправлена на неподдерживаемый адрес.");
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(12000); connection.setReadTimeout(18000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36 LAST-FREQUENCY");
            connection.setRequestProperty("Accept", "application/json,text/html,*/*"); connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location"); connection.disconnect();
                if (location == null || redirects == 4) throw new IllegalStateException("Музыкальная площадка перенаправила слишком много раз.");
                current = new URL(current, location); continue;
            }
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) { connection.disconnect(); throw new IllegalStateException("Музыкальная площадка не вернула данные (HTTP " + status + ")."); }
            ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read;
            try (InputStream input = stream) { while ((read = input.read(buffer)) != -1) { if (output.size() + read > MAX_RESPONSE_BYTES) throw new IllegalStateException("Ответ музыкальной площадки слишком большой."); output.write(buffer, 0, read); } }
            finally { connection.disconnect(); }
            if (status >= 400) throw new IllegalStateException("Музыкальная площадка вернула HTTP " + status + ". Проверь, что ссылка публичная.");
            return output.toString("UTF-8");
        }
        throw new IllegalStateException("Не удалось открыть ссылку музыкальной площадки.");
    }

    private static boolean hostAllowed(String value, String[] allowedHosts) {
        String host = value.toLowerCase(Locale.ROOT);
        for (String domain : allowedHosts) if (host.equals(domain) || host.endsWith("." + domain)) return true;
        return false;
    }

    private static String spotifyType(String value) {
        if (value == null) return "";
        if (value.matches("(?i)spotify:(playlist|track|album):[A-Za-z0-9]+")) return value.split(":")[1].toLowerCase(Locale.ROOT);
        Matcher matcher = SPOTIFY_PATH.matcher(Uri.parse(value).getPath() == null ? "" : Uri.parse(value).getPath());
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : "";
    }

    private static String spotifyId(String value) {
        if (value == null) return "";
        if (value.matches("(?i)spotify:(?:playlist|track|album):[A-Za-z0-9]+")) return value.substring(value.lastIndexOf(':') + 1);
        Matcher matcher = SPOTIFY_PATH.matcher(Uri.parse(value).getPath() == null ? "" : Uri.parse(value).getPath());
        return matcher.find() ? matcher.group(2) : "";
    }

    private static String spotifyCover(JSONObject entity) {
        JSONArray images = entity.optJSONArray("images");
        if (images != null && images.length() > 0) { JSONObject image = images.optJSONObject(0); if (image != null && !image.optString("url", "").isEmpty()) return image.optString("url"); }
        JSONObject cover = entity.optJSONObject("coverArt"); JSONArray sources = cover == null ? null : cover.optJSONArray("sources");
        if (sources != null && sources.length() > 0) { JSONObject item = sources.optJSONObject(0); if (item != null) return item.optString("url", ""); }
        return "";
    }

    private static String spotifyTrackId(JSONObject item) {
        String uri = item.optString("uri", ""); if (uri.startsWith("spotify:track:")) return uri.substring("spotify:track:".length());
        return item.optString("id", "");
    }

    private static String appleArtwork(String value) { return value.replace("100x100bb", "600x600bb").replace("{w}x{h}bb.{f}", "600x600bb.jpg"); }
    private static long millisOrSeconds(long value) { return value > 10000 ? value / 1000 : value; }
    private static String enc(String value) { try { return URLEncoder.encode(value, "UTF-8"); } catch (Exception ignored) { return value; } }
    private static String first(String... values) { for (String value : values) if (value != null && !value.trim().isEmpty() && !"null".equals(value)) return value.trim(); return ""; }
    private static String htmlDecode(String value) { return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString(); }
}
