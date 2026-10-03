package com.kitt.asrprobe;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.*;
import android.content.res.XmlResourceParser;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.util.Log;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Standalone public-API probe. Does not link to, replace, or configure Yantu. */
public class ProbeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextView status;
    private LinearLayout layout;
    private boolean active;
    private int attempt;
    private long started;
    private String selected, expected;
    private Runnable timeout;
    private static final String ACTION = RecognitionService.SERVICE_INTERFACE;

    private void log(String event, Object... pairs) {
        try {
            JSONObject row = new JSONObject();
            row.put("wallTimeMs", System.currentTimeMillis());
            row.put("event", event);
            row.put("attempt", attempt);
            if (attempt > 0) {
                row.put("elapsedMs", SystemClock.elapsedRealtime() - started);
                row.put("component", selected);
                row.put("expected", expected);
            }
            for (int i=0; i<pairs.length; i+=2) row.put(pairs[i].toString(), pairs[i+1] == null ? JSONObject.NULL : pairs[i+1]);
            String line = row.toString();
            Log.i("GoogleASRProbe", line);
            try (FileOutputStream stream = openFileOutput("probe.jsonl", MODE_APPEND)) {
                stream.write((line + "\n").getBytes("UTF-8"));
            }
        } catch (Exception e) { Log.e("GoogleASRProbe", "log failed", e); }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 30, 24, 24);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout); setContentView(scroll);
        status = new TextView(this); status.setTextSize(20); layout.addView(status);
        status.setText("独立诊断，不改沿途。\n点句子后等“可以说话”，再说对应句子。\n请先完成三句 Google 测试。");
        enumerate();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        else availability();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results); availability();
    }
    private void availability() {
        try { log("availability", "recognition", SpeechRecognizer.isRecognitionAvailable(this),
            "onDevice", SpeechRecognizer.isOnDeviceRecognitionAvailable(this),
            "recordAudioGranted", checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED); }
        catch(Exception e) { log("availabilityException", "exception", e.toString()); }
    }
    private void enumerate() {
        PackageManager pm = getPackageManager();
        log("environment", "sdk", Build.VERSION.SDK_INT, "model", Build.MODEL,
            "uid", android.os.Process.myUid(), "targetSdk", getApplicationInfo().targetSdkVersion,
            "defaultRecognizer", Settings.Secure.getString(getContentResolver(), "voice_recognition_service"),
            "defaultIme", Settings.Secure.getString(getContentResolver(), "default_input_method"),
            "voiceInteractionService", Settings.Secure.getString(getContentResolver(), "voice_interaction_service"));
        for (String pkg : new String[]{"com.google.android.googlequicksearchbox", "com.google.android.tts", "com.google.android.apps.speechservices", "com.google.android.inputmethod.latin"}) {
            try { PackageInfo pi = pm.getPackageInfo(pkg, 0); log("googlePackage", "package", pkg, "label", pm.getApplicationLabel(pi.applicationInfo), "version", pi.versionName, "versionCode", pi.getLongVersionCode(), "appEnabled", pi.applicationInfo.enabled); }
            catch (PackageManager.NameNotFoundException e) { log("googlePackageAbsent", "package", pkg); }
        }
        List<ResolveInfo> normal = pm.queryIntentServices(new Intent(ACTION), PackageManager.GET_META_DATA);
        HashSet<String> enabled = new HashSet<>();
        for (ResolveInfo ri : normal) enabled.add(new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name).flattenToString());
        log("queryNormal", "components", new JSONArray(enabled));
        for (ResolveInfo ri : pm.queryIntentServices(new Intent(ACTION), PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_COMPONENTS)) {
            ServiceInfo si = ri.serviceInfo; ComponentName cn = new ComponentName(si.packageName, si.name);
            String component = cn.flattenToString();
            try {
                PackageInfo pi = pm.getPackageInfo(si.packageName, 0);
                ResolveInfo explicit = pm.resolveService(new Intent(ACTION).setComponent(cn), 0);
                String settingsActivity = null;
                try (XmlResourceParser xml = si.loadXmlMetaData(pm, RecognitionService.SERVICE_META_DATA)) {
                    if (xml != null) { while (xml.next() != XmlResourceParser.END_DOCUMENT) {
                        if (xml.getEventType() == XmlResourceParser.START_TAG && "recognition-service".equals(xml.getName())) {
                            settingsActivity = xml.getAttributeValue("http://schemas.android.com/apk/res/android", "settingsActivity"); break;
                        }
                    } }
                } catch(Exception e) { log("metadataException", "service", component, "exception", e.toString()); }
                log("service", "package", si.packageName, "service", component,
                    "serviceEnabled", si.enabled, "appEnabled", si.applicationInfo.enabled,
                    "componentEnabledSetting", pm.getComponentEnabledSetting(cn),
                    "appEnabledSetting", pm.getApplicationEnabledSetting(si.packageName),
                    "exported", si.exported, "permission", si.permission,
                    "version", pi.versionName, "versionCode", pi.getLongVersionCode(),
                    "normalQuery", enabled.contains(component), "explicitResolve", explicit != null,
                    "settingsActivity", settingsActivity);
                if (enabled.contains(component) && si.exported && si.packageName.startsWith("com.google.")) {
                    for (String phrase : new String[]{"再讲一点", "三星堆为什么这么有名", "马尔康有什么值得看的"}) {
                        Button button = new Button(this); button.setText("Google 测试：" + phrase);
                        layout.addView(button); button.setOnClickListener(v -> start(component, phrase));
                    }
                }
            } catch (Exception e) { log("serviceException", "service", component, "exception", e.toString()); }
        }
        Button defaultButton = new Button(this); defaultButton.setText("仅测试系统默认服务"); layout.addView(defaultButton);
        defaultButton.setOnClickListener(v -> start(null, "系统默认服务测试"));
    }
    private Object bundle(Bundle value) {
        if (value == null) return JSONObject.NULL;
        JSONObject json = new JSONObject();
        for (String key : value.keySet()) try {
            Object item = value.get(key);
            if (item instanceof ArrayList) item = new JSONArray((ArrayList<?>)item);
            else if (item instanceof float[]) { JSONArray a = new JSONArray(); for(float f:(float[])item) a.put(f); item=a; }
            else if (item != null && !(item instanceof Number) && !(item instanceof Boolean) && !(item instanceof String)) item=item.toString();
            json.put(key, item == null ? JSONObject.NULL : item);
        } catch(Exception e) { }
        return json;
    }
    private void start(String component, String phrase) {
        if (active) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { status.setText("需要诊断 App 的麦克风权限"); return; }
        active=true; attempt++; started=SystemClock.elapsedRealtime(); expected=phrase;
        selected=component==null ? Settings.Secure.getString(getContentResolver(), "voice_recognition_service") : component;
        log("create", "mode", component==null ? "default" : "explicit", "language", "zh-CN");
        status.setText("正在启动：" + phrase);
        try {
            recognizer = component == null ? SpeechRecognizer.createSpeechRecognizer(this) :
                SpeechRecognizer.createSpeechRecognizer(this, ComponentName.unflattenFromString(component));
            recognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle p) { log("ready", "bundle", bundle(p)); status.setText("可以说话：" + expected); }
                public void onBeginningOfSpeech() { log("beginningOfSpeech"); }
                public void onRmsChanged(float rms) { log("RMS", "rmsDb", rms); }
                public void onBufferReceived(byte[] buffer) { log("bufferReceived", "bytes", buffer==null ? 0 : buffer.length); }
                public void onEndOfSpeech() { log("endOfSpeech"); status.setText("等待结果："+expected); }
                public void onError(int error) { log("error", "code", error); status.setText("失败，原始 error code="+error+"\n"+expected); finishAttempt(); }
                public void onResults(Bundle results) { log("finalResults", "bundle", bundle(results)); status.setText("结果："+results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)+"\n请点下一句。"); finishAttempt(); }
                public void onPartialResults(Bundle results) { log("partialResults", "bundle", bundle(results)); }
                public void onEvent(int type, Bundle p) { log("event", "type", type, "bundle", bundle(p)); }
                public void onSegmentResults(Bundle results) { log("segmentResults", "bundle", bundle(results)); }
                public void onEndOfSegmentedSession() { log("endOfSegmentedSession"); finishAttempt(); }
                public void onLanguageDetection(Bundle p) { log("languageDetection", "bundle", bundle(p)); }
            });
            Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            request.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            request.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            timeout=() -> { log("probeTimeout", "limitMs", 30000); status.setText("探测超时，无终结 callback"); finishAttempt(); };
            handler.postDelayed(timeout, 30000);
            log("startListening"); recognizer.startListening(request);
        } catch(Exception e) { log("exception", "exception", e.toString()); status.setText(e.toString()); finishAttempt(); }
    }
    private void finishAttempt() {
        active=false; if(timeout!=null) handler.removeCallbacks(timeout);
        SpeechRecognizer old=recognizer; recognizer=null;
        handler.post(() -> { if(old!=null) { old.cancel(); old.destroy(); } });
    }
    @Override public void onStop() { super.onStop(); if(active) { log("cancel", "reason", "activityStopped"); finishAttempt(); } }
}
