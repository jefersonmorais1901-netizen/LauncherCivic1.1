package com.civic.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private WebView web;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean locStarted = false;
    private String lastMedia = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        applyImmersiveMode();
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");

        askLocation();
        h.postDelayed(new Runnable() {
            public void run() {
                pushMedia();
                h.postDelayed(this, 2000);
            }
        }, 2500);
        h.postDelayed(new Runnable() {
            public void run() { firstRunNotificationAccess(); }
        }, 5000);
    }


    // Oculta as barras do sistema para aproveitar a tela inteira da multimídia.
    private void applyImmersiveMode() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    // ---------- JS <-> Android ----------
    public class Bridge {
        @JavascriptInterface
        public void open(final String n) {
            runOnUiThread(new Runnable() { public void run() { openName(n); } });
        }

        @JavascriptInterface
        public void launch(final String p) {
            runOnUiThread(new Runnable() {
                public void run() { if (!tryAny(p)) toast("Não foi possível abrir o app"); }
            });
        }

        @JavascriptInterface
        public void key(String k) {
            if ("play".equals(k)) sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }

        @JavascriptInterface
        public String apps() {
            try {
                PackageManager pm = getPackageManager();
                Intent m = new Intent(Intent.ACTION_MAIN);
                m.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> l = pm.queryIntentActivities(m, 0);
                Collections.sort(l, new ResolveInfo.DisplayNameComparator(pm));
                JSONArray arr = new JSONArray();
                for (ResolveInfo r : l) {
                    String pkg = r.activityInfo.packageName;
                    if (pkg.equals(getPackageName())) continue;
                    JSONObject o = new JSONObject();
                    o.put("l", String.valueOf(r.loadLabel(pm)));
                    o.put("p", pkg);
                    o.put("i", iconB64(r.loadIcon(pm)));
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }
    }

    // ---------- Atalhos (edite aqui os pacotes dos apps) ----------
    private void openName(String n) {
        switch (n) {
            case "Waze": if (!tryAny("com.waze")) nf(); break;
            case "Spotify": if (!tryAny("com.spotify.music")) nf(); break;
            case "Vídeos": if (!tryAny("com.google.android.youtube", "com.mxtech.videoplayer.ad", "com.android.gallery3d")) nf(); break;
            case "GPS":
            case "Navegação": if (!tryAny("com.google.android.apps.maps", "com.waze")) nf(); break;
            case "Chrome": if (!tryAny("com.android.chrome")) nf(); break;
            case "Loja": if (!tryAny("com.android.vending")) nf(); break;
            case "Música": music(); break;
            case "Bluetooth": settings(Settings.ACTION_BLUETOOTH_SETTINGS); break;
            case "Sistema":
            case "Ajustes": settings(Settings.ACTION_SETTINGS); break;
            case "Faixa anterior": sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS); break;
            case "Próxima faixa": sendKey(KeyEvent.KEYCODE_MEDIA_NEXT); break;
            default: toast("Atalho ainda não configurado: " + n);
        }
    }

    private boolean tryAny(String... pkgs) {
        for (String p : pkgs) {
            Intent i = getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            }
        }
        return false;
    }

    private void music() {
        try {
            Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            nf();
        }
    }

    private void settings(String action) {
        try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { nf(); }
    }

    private void nf() { toast("App não encontrado nesta central"); }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    private void sendKey(int code) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    private String iconB64(Drawable d) {
        Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, 96, 96);
        d.draw(c);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, o);
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }

    private void js(String code) { web.evaluateJavascript(code, null); }

    // ---------- Música tocando (título, artista, capa) ----------
    private void pushMedia() {
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> cs = msm.getActiveSessions(new ComponentName(this, MediaListener.class));
            MediaController pick = null;
            for (MediaController c : cs) {
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) { pick = c; break; }
            }
            if (pick == null && !cs.isEmpty()) pick = cs.get(0);
            String t = "", a = "", cover = "";
            boolean playing = false;
            if (pick != null) {
                PlaybackState st = pick.getPlaybackState();
                playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
                MediaMetadata md = pick.getMetadata();
                if (md != null) {
                    t = nz(md.getString(MediaMetadata.METADATA_KEY_TITLE));
                    a = nz(md.getString(MediaMetadata.METADATA_KEY_ARTIST));
                    Bitmap bm = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                    if (bm != null) {
                        Bitmap sm = Bitmap.createScaledBitmap(bm, 240, 240, true);
                        ByteArrayOutputStream o = new ByteArrayOutputStream();
                        sm.compress(Bitmap.CompressFormat.JPEG, 80, o);
                        cover = Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
                    }
                }
            }
            String key = t + "|" + a + "|" + playing + "|" + cover.length();
            if (key.equals(lastMedia)) return;
            lastMedia = key;
            js("window.onMedia&&onMedia(" + JSONObject.quote(t) + "," + JSONObject.quote(a) + "," + playing + "," + JSONObject.quote(cover) + ")");
        } catch (Exception e) {
            // sem acesso às notificações ainda: o cartão fica no modo simples
        }
    }

    private String nz(String s) { return s == null ? "" : s; }

    private void firstRunNotificationAccess() {
        String en = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean ok = en != null && en.contains(getPackageName());
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        if (!ok && !p.getBoolean("asked", false)) {
            p.edit().putBoolean("asked", true).apply();
            Toast.makeText(this, "Ative o acesso às notificações do Launcher Civic para mostrar a música tocando", Toast.LENGTH_LONG).show();
            try { startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); } catch (Exception e) { }
        }
    }

    // ---------- Velocidade (GPS) ----------
    private void askLocation() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            startLoc();
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) startLoc();
    }

    private void startLoc() {
        if (locStarted) return;
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, new LocationListener() {
                public void onLocationChanged(Location l) {
                    if (l.hasSpeed()) js("window.onSpeed&&onSpeed(" + Math.round(l.getSpeed() * 3.6f) + ")");
                }
                public void onStatusChanged(String p, int s, Bundle e) { }
                public void onProviderEnabled(String p) { }
                public void onProviderDisabled(String p) { }
            });
            locStarted = true;
        } catch (SecurityException e) {
            // sem permissão de localização
        }
    }

    // ---------- Comportamento de launcher ----------
    private void closeDrawer() {
        js("document.querySelectorAll('.dr').forEach(function(d){d.classList.remove('on')})");
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        closeDrawer();
    }

    @Override
    public void onBackPressed() {
        closeDrawer(); // launcher não fecha com o botão voltar
    }
}
