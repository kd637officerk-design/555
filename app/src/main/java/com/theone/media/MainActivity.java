package com.theone.media;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import androidx.media.MediaMetadataCompat;
import androidx.media.session.MediaSessionCompat;
import androidx.media.session.PlaybackStateCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder stateBuilder;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private static final String CHANNEL_ID = "the_one_media";
    private static final int NOTIFICATION_ID = 1001;
    private String currentTitle = "THE ONE";
    private String currentArtist = "Media Deck";
    private Bitmap currentArt;
    private boolean isPlaying = false;
    private long currentPosition = 0;
    private long duration = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen immersive
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectMediaBridge();
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        // JS bridge for media controls
        webView.addJavascriptInterface(new MediaBridge(), "AndroidMedia");

        // Load the hyper-realistic media deck
        webView.loadUrl("file:///android_asset/index.html");

        // Media Session setup
        setupMediaSession();
        createNotificationChannel();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    }

    private void injectMediaBridge() {
        String js =
            "(function(){" +
            "  if(window.__theOneBridgeInjected) return;" +
            "  window.__theOneBridgeInjected = true;" +
            "  window.AndroidMediaBridge = {" +
            "    play: function(){ var a=document.querySelector('audio,video'); if(a) a.play(); }," +
            "    pause: function(){ var a=document.querySelector('audio,video'); if(a) a.pause(); }," +
            "    next: function(){ /* deck may handle */ }," +
            "    prev: function(){ }," +
            "    seekTo: function(ms){ var a=document.querySelector('audio,video'); if(a) a.currentTime = ms/1000; }" +
            "  };" +
            "  function sync(){" +
            "    var a = document.querySelector('audio,video');" +
            "    if(!a || typeof AndroidMedia==='undefined') return;" +
            "    try {" +
            "      var title = a.title || document.title || 'THE ONE';" +
            "      var artist = 'Media Deck';" +
            "      var dur = (a.duration||0)*1000;" +
            "      AndroidMedia.updateMetadata(title, artist, dur|0, null);" +
            "      AndroidMedia.setPlaying(!a.paused, (a.currentTime||0)*1000);" +
            "    } catch(e){}" +
            "  }" +
            "  setInterval(sync, 1200);" +
            "  document.addEventListener('play', sync, true);" +
            "  document.addEventListener('pause', sync, true);" +
            "  document.addEventListener('timeupdate', function(){}, true);" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, "TheOneMediaSession");
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        stateBuilder = new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_STOP |
                PlaybackStateCompat.ACTION_SEEK_TO
            );

        mediaSession.setPlaybackState(stateBuilder.setState(
            PlaybackStateCompat.STATE_PAUSED, 0, 1.0f
        ).build());

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                isPlaying = true;
                requestAudioFocus();
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                // Tell webview
                runOnUiThread(() -> webView.evaluateJavascript(
                    "if(window.AndroidMediaBridge){window.AndroidMediaBridge.play();}", null));
                showNotification();
            }

            @Override
            public void onPause() {
                isPlaying = false;
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                runOnUiThread(() -> webView.evaluateJavascript(
                    "if(window.AndroidMediaBridge){window.AndroidMediaBridge.pause();}", null));
                showNotification();
            }

            @Override
            public void onSkipToNext() {
                runOnUiThread(() -> webView.evaluateJavascript(
                    "if(window.AndroidMediaBridge){window.AndroidMediaBridge.next();}", null));
            }

            @Override
            public void onSkipToPrevious() {
                runOnUiThread(() -> webView.evaluateJavascript(
                    "if(window.AndroidMediaBridge){window.AndroidMediaBridge.prev();}", null));
            }

            @Override
            public void onStop() {
                isPlaying = false;
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
                abandonAudioFocus();
                cancelNotification();
            }

            @Override
            public void onSeekTo(long pos) {
                currentPosition = pos;
                runOnUiThread(() -> webView.evaluateJavascript(
                    "if(window.AndroidMediaBridge){window.AndroidMediaBridge.seekTo(" + pos + ");}", null));
                updatePlaybackState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
            }
        });

        mediaSession.setActive(true);
    }

    private void updatePlaybackState(int state) {
        stateBuilder.setState(state, currentPosition, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void updateMetadata() {
        MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        if (currentArt != null) {
            meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArt);
        }
        mediaSession.setMetadata(meta.build());
    }

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        // pause
                    }
                })
                .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            audioManager.abandonAudioFocus(null);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "THE ONE Media Controls", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("System media controls for THE ONE");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private void showNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "Prev",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
            .addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, "Play/Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE))
            .addAction(android.R.drawable.ic_media_next, "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT));

        if (currentArt != null) {
            builder.setLargeIcon(currentArt);
        }

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, builder.build());
    }

    private void cancelNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID);
    }

    // Bridge called from JS in the media deck
    public class MediaBridge {
        @JavascriptInterface
        public void updateMetadata(String title, String artist, long dur, String artBase64) {
            currentTitle = title != null ? title : "THE ONE";
            currentArtist = artist != null ? artist : "Media Deck";
            duration = dur;
            if (artBase64 != null && !artBase64.isEmpty()) {
                try {
                    byte[] decoded = android.util.Base64.decode(artBase64, android.util.Base64.DEFAULT);
                    currentArt = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                } catch (Exception e) {
                    currentArt = null;
                }
            }
            runOnUiThread(() -> {
                updateMetadata();
                showNotification();
            });
        }

        @JavascriptInterface
        public void setPlaying(boolean playing, long position) {
            isPlaying = playing;
            currentPosition = position;
            runOnUiThread(() -> {
                updatePlaybackState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                showNotification();
            });
        }

        @JavascriptInterface
        public void seek(long position) {
            currentPosition = position;
            runOnUiThread(() -> updatePlaybackState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED));
        }
    }

    @Override
    protected void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        cancelNotification();
        abandonAudioFocus();
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
