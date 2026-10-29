package com.lastfrequency.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PlaybackService extends Service {
    public static final String ACTION_SET_QUEUE = "com.lastfrequency.player.SET_QUEUE";
    public static final String ACTION_PLAY = "com.lastfrequency.player.PLAY";
    public static final String ACTION_PAUSE = "com.lastfrequency.player.PAUSE";
    public static final String ACTION_TOGGLE = "com.lastfrequency.player.TOGGLE";
    public static final String ACTION_NEXT = "com.lastfrequency.player.NEXT";
    public static final String ACTION_PREVIOUS = "com.lastfrequency.player.PREVIOUS";
    public static final String ACTION_STATE_CHANGED = "com.lastfrequency.player.PLAYBACK_STATE";
    public static final String EXTRA_QUEUE = "queue";
    public static final String EXTRA_INDEX = "index";

    private static final String CHANNEL_ID = "last_frequency_playback";
    private static final int NOTIFICATION_ID = 2201;
    private static final String TAG = "LastFrequencyPlayback";

    private final IBinder binder = new LocalBinder();
    private final ArrayList<Track> queue = new ArrayList<>();
    private MediaSession mediaSession;
    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private BassBoost bassBoost;
    private int bassBoostX10;
    private int playbackVolume = 100;
    private int currentIndex = -1;
    private Listener listener;
    private boolean prepared;

    public interface Listener {
        void onPlaybackStateChanged(String title, String artist, String uri, boolean playing);
    }

    public class LocalBinder extends Binder {
        public PlaybackService getService() { return PlaybackService.this; }
    }

    private static final class Track {
        final String uri;
        final String title;
        final String artist;
        final String album;
        final String sourceUri;
        final String coverUrl;
        final Map<String, String> headers;

        Track(String uri, String title, String artist, String album, String sourceUri, String coverUrl, Map<String, String> headers) {
            this.uri = uri;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.sourceUri = sourceUri == null ? "" : sourceUri;
            this.coverUrl = coverUrl == null ? "" : coverUrl;
            this.headers = headers == null ? new HashMap<>() : headers;
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        createNotificationChannel();
        mediaSession = new MediaSession(this, "LastFrequencyPlayback");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resumePlayback(); }
            @Override public void onPause() { pausePlayback(); }
            @Override public void onSkipToNext() { nextTrack(); }
            @Override public void onSkipToPrevious() { previousTrack(); }
            @Override public void onSeekTo(long position) {
                if (player != null && prepared) {
                    player.seekTo((int) Math.max(0, Math.min(position, player.getDuration())));
                    publishState();
                }
            }
        });
        mediaSession.setSessionActivity(PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        mediaSession.setActive(true);
        updateSessionState(PlaybackState.STATE_NONE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_SET_QUEUE.equals(action)) {
            loadQueue(intent.getStringExtra(EXTRA_QUEUE), intent.getIntExtra(EXTRA_INDEX, 0));
            startCurrent();
        } else if (ACTION_PLAY.equals(action)) {
            resumePlayback();
        } else if (ACTION_PAUSE.equals(action)) {
            pausePlayback();
        } else if (ACTION_TOGGLE.equals(action)) {
            if (isPlaying()) pausePlayback(); else resumePlayback();
        } else if (ACTION_NEXT.equals(action)) {
            nextTrack();
        } else if (ACTION_PREVIOUS.equals(action)) {
            previousTrack();
        }
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public void onDestroy() {
        abandonAudioFocus();
        releasePlayer();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        publishState();
    }

    public String getCurrentTitle() { Track track = currentTrack(); return track == null ? "" : track.title; }
    public String getCurrentArtist() { Track track = currentTrack(); return track == null ? "" : track.artist; }
    public String getCurrentUri() { Track track = currentTrack(); return track == null ? "" : (track.sourceUri.isEmpty() ? track.uri : track.sourceUri); }
    public String getCurrentCoverUrl() { Track track = currentTrack(); return track == null ? "" : track.coverUrl; }
    public boolean isPlaying() { return player != null && prepared && player.isPlaying(); }
    public int getCurrentPosition() { try { return player != null && prepared ? player.getCurrentPosition() : 0; } catch (IllegalStateException ignored) { return 0; } }
    public int getDuration() { try { return player != null && prepared ? player.getDuration() : 0; } catch (IllegalStateException ignored) { return 0; } }
    public int getVolume() { return playbackVolume; }
    public void seekTo(int positionMs) {
        if (player == null || !prepared) return;
        try { player.seekTo(Math.max(0, Math.min(positionMs, player.getDuration()))); publishState(); }
        catch (IllegalStateException ignored) { }
    }
    public void setVolume(int percent) {
        playbackVolume = Math.max(0, Math.min(100, percent));
        float value = playbackVolume / 100f;
        if (player != null) try { player.setVolume(value, value); } catch (IllegalStateException ignored) { }
    }
    public void setBassBoostX10(int value) {
        bassBoostX10 = Math.max(0, Math.min(100, value));
        applyBassBoost();
    }

    private void applyBassBoost() {
        try {
            if (bassBoost == null && player != null) bassBoost = new BassBoost(0, player.getAudioSessionId());
            if (bassBoost != null) {
                bassBoost.setStrength((short) Math.min(1000, bassBoostX10 * 100));
                bassBoost.setEnabled(bassBoostX10 > 0);
            }
        } catch (Exception error) { Log.w(TAG, "Bass boost is unavailable", error); }
    }

    private Track currentTrack() {
        return currentIndex >= 0 && currentIndex < queue.size() ? queue.get(currentIndex) : null;
    }

    private void loadQueue(String serialized, int index) {
        queue.clear();
        try {
            JSONArray array = new JSONArray(serialized == null ? "[]" : serialized);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String uri = item.optString("uri", "");
                if (uri.isEmpty()) continue;
                JSONObject headerData = item.optJSONObject("headers");
                Map<String, String> headers = new HashMap<>();
                if (headerData != null) for (java.util.Iterator<String> keys = headerData.keys(); keys.hasNext();) {
                    String key = keys.next(); headers.put(key, headerData.optString(key, ""));
                }
                queue.add(new Track(uri, item.optString("title", "Без названия"),
                        item.optString("artist", "Неизвестный исполнитель"), item.optString("album", ""),
                        item.optString("source_uri", ""), item.optString("cover_url", ""), headers));
            }
        } catch (Exception error) {
            Log.w(TAG, "Could not read playback queue", error);
        }
        currentIndex = queue.isEmpty() ? -1 : Math.max(0, Math.min(index, queue.size() - 1));
    }

    private void startCurrent() {
        Track track = currentTrack();
        if (track == null) {
            stopSelf();
            return;
        }
        releasePlayer();
        prepared = false;
        try {
            updateMetadata(track, 0);
            startForeground(NOTIFICATION_ID, buildNotification());
            player = new MediaPlayer();
            setVolume(playbackVolume);
            applyBassBoost();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setOnPreparedListener(mediaPlayer -> {
                prepared = true;
                if (!requestAudioFocus()) {
                    pausePlayback();
                    return;
                }
                mediaPlayer.start();
                updateMetadata(currentTrack(), mediaPlayer.getDuration());
                updateSessionState(PlaybackState.STATE_PLAYING);
                refreshNotification();
                publishState();
            });
            player.setOnCompletionListener(mediaPlayer -> nextTrack());
            player.setOnErrorListener((mediaPlayer, what, extra) -> {
                Log.w(TAG, "MediaPlayer error " + what + ", " + extra);
                updateSessionState(PlaybackState.STATE_ERROR);
                refreshNotification();
                publishState();
                return true;
            });
            player.setDataSource(this, Uri.parse(track.uri), track.headers);
            player.prepareAsync();
            updateSessionState(PlaybackState.STATE_BUFFERING);
            refreshNotification();
            publishState();
        } catch (IOException | IllegalArgumentException error) {
            Log.w(TAG, "Could not open audio", error);
            updateSessionState(PlaybackState.STATE_ERROR);
            publishState();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) return true;
        AudioManager.OnAudioFocusChangeListener focusListener = change -> {
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                pausePlayback();
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK && player != null) {
                float ducked = playbackVolume / 100f * 0.25f;
                player.setVolume(ducked, ducked);
            } else if (change == AudioManager.AUDIOFOCUS_GAIN && player != null) {
                float value = playbackVolume / 100f;
                player.setVolume(value, value);
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setOnAudioFocusChangeListener(focusListener).build();
            }
            return audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
    }

    private void resumePlayback() {
        if (player == null) {
            if (currentTrack() != null) startCurrent();
            return;
        }
        if (!prepared) return;
        if (!requestAudioFocus()) return;
        try {
            if (!player.isPlaying()) player.start();
            updateSessionState(PlaybackState.STATE_PLAYING);
            refreshNotification();
            publishState();
        } catch (IllegalStateException error) {
            startCurrent();
        }
    }

    private void pausePlayback() {
        try {
            if (player != null && prepared && player.isPlaying()) player.pause();
        } catch (IllegalStateException ignored) { }
        updateSessionState(PlaybackState.STATE_PAUSED);
        refreshNotification();
        publishState();
    }

    private void nextTrack() {
        if (queue.isEmpty()) return;
        currentIndex = (currentIndex + 1) % queue.size();
        startCurrent();
    }

    private void previousTrack() {
        if (player != null && prepared) {
            try {
                if (player.getCurrentPosition() > 5000) {
                    player.seekTo(0);
                    publishState();
                    return;
                }
            } catch (IllegalStateException ignored) { }
        }
        if (queue.isEmpty()) return;
        currentIndex = (currentIndex - 1 + queue.size()) % queue.size();
        startCurrent();
    }

    private void updateMetadata(Track track, long duration) {
        if (track == null || mediaSession == null) return;
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, track.uri);
        if (duration > 0) metadata.putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
        Bitmap cover = readArtwork(track.uri);
        if (cover != null) metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover);
        mediaSession.setMetadata(metadata.build());
    }

    private Bitmap readArtwork(String uri) {
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, Uri.parse(uri));
            byte[] data = retriever.getEmbeddedPicture();
            if (data != null && data.length > 0) {
                Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (decoded != null && Math.max(decoded.getWidth(), decoded.getHeight()) > 512) {
                    float scale = 512f / Math.max(decoded.getWidth(), decoded.getHeight());
                    Bitmap scaled = Bitmap.createScaledBitmap(decoded,
                            Math.max(1, Math.round(decoded.getWidth() * scale)),
                            Math.max(1, Math.round(decoded.getHeight() * scale)), true);
                    if (scaled != decoded) decoded.recycle();
                    return scaled;
                }
                return decoded;
            }
        } catch (Exception ignored) { }
        finally { try { retriever.release(); } catch (Exception ignored) { } }
        return null;
    }

    private void updateSessionState(int state) {
        if (mediaSession == null) return;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO;
        PlaybackState.Builder playback = new PlaybackState.Builder().setActions(actions);
        long position = 0;
        if (player != null && prepared) {
            try { position = player.getCurrentPosition(); } catch (IllegalStateException ignored) { }
        }
        playback.setState(state, position, state == PlaybackState.STATE_PLAYING ? 1f : 0f, SystemClock.elapsedRealtime());
        mediaSession.setPlaybackState(playback.build());
    }

    private Notification buildNotification() {
        Track track = currentTrack();
        String title = track == null ? "LAST FREQUENCY" : track.title;
        String artist = track == null ? "" : track.artist;
        Bitmap cover = track == null ? null : readArtwork(track.uri);
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title).setContentText(artist).setContentIntent(content)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(isPlaying())
                .setOnlyAlertOnce(true);
        if (cover != null) builder.setLargeIcon(cover);
        builder.addAction(action(android.R.drawable.ic_media_previous, "Назад", ACTION_PREVIOUS, 1));
        builder.addAction(action(isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying() ? "Пауза" : "Играть", isPlaying() ? ACTION_PAUSE : ACTION_PLAY, 2));
        builder.addAction(action(android.R.drawable.ic_media_next, "Дальше", ACTION_NEXT, 3));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
        }
        return builder.build();
    }

    private Notification.Action action(int icon, String label, String action, int requestCode) {
        Intent intent = new Intent(this, PlaybackService.class).setAction(action);
        PendingIntent pending = PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(icon, label, pending).build();
    }

    private void refreshNotification() {
        if (currentTrack() == null) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void publishState() {
        Track track = currentTrack();
        String title = track == null ? "" : track.title;
        String artist = track == null ? "" : track.artist;
        String uri = track == null ? "" : track.uri;
        boolean playing = isPlaying();
        Intent update = new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName())
                .putExtra("title", title).putExtra("artist", artist)
                .putExtra("uri", uri).putExtra("playing", playing);
        sendBroadcast(update);
        if (listener != null) listener.onPlaybackStateChanged(title, artist, uri, playing);
    }

    private void releasePlayer() {
        if (bassBoost != null) {
            try { bassBoost.setEnabled(false); bassBoost.release(); } catch (Exception ignored) { }
            bassBoost = null;
        }
        if (player != null) {
            try { player.setOnPreparedListener(null); player.setOnCompletionListener(null); player.release(); }
            catch (RuntimeException ignored) { }
            player = null;
        }
        prepared = false;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Воспроизведение музыки",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Управление воспроизведением LAST FREQUENCY");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }
}
