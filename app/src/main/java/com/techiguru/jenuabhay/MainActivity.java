package com.techiguru.jenuabhay;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.techiguru.jenuabhay.adapter.ChatAdapter;
import com.techiguru.jenuabhay.model.Message;
import com.techiguru.jenuabhay.repository.AIRepository;
import com.techiguru.jenuabhay.repository.NoteRepository;
import com.techiguru.jenuabhay.service.VoiceAssistantService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String TAG = "JenuVoice";
    
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private ExtendedFloatingActionButton fabMic;
    private View waveContainer;
    private TextView tvStatus;
    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<Message> messageList;
    private NoteRepository noteRepository;
    private AIRepository aiRepository;
    private Animation pulseAnimation;
    
    private boolean isListening = false;
    private boolean shouldListenAfterSpeak = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isFlashlightOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        View mainView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        checkPermissions();
        initSpeech();
        initTTS();
        noteRepository = new NoteRepository(this);
        aiRepository = new AIRepository();
        pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse);
        
        startVoiceService();
    }

    private void startVoiceService() {
        try {
            Intent serviceIntent = new Intent(this, VoiceAssistantService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Service Error", e);
        }
    }

    private void initViews() {
        fabMic = findViewById(R.id.fab_mic);
        waveContainer = findViewById(R.id.wave_container);
        tvStatus = findViewById(R.id.tv_status);
        chatRecyclerView = findViewById(R.id.chat_recycler_view);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);

        fabMic.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });
    }

    private void checkPermissions() {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.RECORD_AUDIO);
        p.add(Manifest.permission.CAMERA);
        p.add(Manifest.permission.CALL_PHONE);
        p.add(Manifest.permission.SEND_SMS);
        p.add(Manifest.permission.READ_CONTACTS);
        if (android.os.Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);

        List<String> request = new ArrayList<>();
        for (String s : p) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                request.add(s);
            }
        }
        if (!request.isEmpty()) {
            ActivityCompat.requestPermissions(this, request.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void initSpeech() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this, 
                new ComponentName("com.google.android.googlequicksearchbox", 
                "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"));
        } catch (Exception e) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
                mainHandler.post(() -> {
                    tvStatus.setText("Listening...");
                    waveContainer.setVisibility(View.VISIBLE);
                    waveContainer.startAnimation(pulseAnimation);
                    fabMic.setText("Listening...");
                });
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                resetUI();
            }

            @Override
            public void onError(int error) {
                Log.e(TAG, "Error: " + error);
                resetUI();
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    initSpeech();
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processCommand(matches.get(0));
                }
                resetUI();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    mainHandler.post(() -> tvStatus.setText(matches.get(0)));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void resetUI() {
        isListening = false;
        mainHandler.post(() -> {
            waveContainer.clearAnimation();
            waveContainer.setVisibility(View.GONE);
            fabMic.setText("Ask Jenu");
        });
    }

    private void initTTS() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {}
                    @Override
                    public void onDone(String id) {
                        if (shouldListenAfterSpeak) {
                            shouldListenAfterSpeak = false;
                            mainHandler.postDelayed(() -> startListening(), 500);
                        }
                    }
                    @Override
                    public void onError(String id) {}
                });
                speak("I'm ready. I am your Voice Automation assistant.", false);
            }
        });
    }

    private void startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (speechRecognizer == null) initSpeech();
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        
        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            initSpeech();
        }
    }

    private void stopListening() {
        if (speechRecognizer != null) speechRecognizer.stopListening();
        resetUI();
    }

    private void speak(String text, boolean listenAfter) {
        shouldListenAfterSpeak = listenAfter;
        addMessage(text, false);
        Bundle p = new Bundle();
        p.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "jenu");
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, p, "jenu");
    }

    private void addMessage(String text, boolean isUser) {
        mainHandler.post(() -> {
            messageList.add(new Message(text, isUser));
            chatAdapter.notifyItemInserted(messageList.size() - 1);
            chatRecyclerView.scrollToPosition(messageList.size() - 1);
        });
    }

    private void processCommand(String command) {
        addMessage(command, true);
        String lower = command.toLowerCase().trim().replaceAll("[^a-zA-Z0-9 ]", "");

        if (lower.startsWith("open")) {
            String appName = lower.replace("open", "").trim();
            handleOpenApp(appName);
        } else if (lower.contains("camera")) {
            speak("Opening camera", false);
            startActivity(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE));
        } else if (lower.contains("flashlight") || lower.contains("torch")) {
            handleFlashlight(lower);
        } else if (lower.contains("send message") || lower.contains("whatsapp") || lower.contains("send a message")) {
            handleSocialMessaging(command);
        } else if (lower.contains("play store") || lower.contains("google play")) {
            handlePlayStore(lower);
        } else if (lower.contains("play music") || lower.contains("spotify") || lower.contains("youtube")) {
            handleMusicAndVideo(lower);
        } else if (lower.contains("call")) {
            speak("Opening dialer", false);
            startActivity(new Intent(Intent.ACTION_DIAL));
        } else if (lower.contains("sms") || lower.contains("message")) {
            speak("Opening messages", false);
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_APP_MESSAGING);
            startActivity(i);
        } else if (lower.contains("save note")) {
            saveNote(command.replace("save note", "").trim());
        } else if (lower.contains("hello") || lower.contains("hi")) {
            speak("Hello! How can I help?", true);
        } else {
            speak("Searching for an answer...", false);
            aiRepository.askAI(command, res -> mainHandler.post(() -> speak(res, true)));
        }
    }

    private void handleFlashlight(String command) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = cameraManager.getCameraIdList();
            if (cameraIdList.length == 0) {
                speak("Flashlight not available on this device", false);
                return;
            }
            String cameraId = cameraIdList[0];
            if (command.contains("on") || command.contains("start")) {
                cameraManager.setTorchMode(cameraId, true);
                isFlashlightOn = true;
                speak("Flashlight turned on", false);
            } else if (command.contains("off") || command.contains("stop")) {
                cameraManager.setTorchMode(cameraId, false);
                isFlashlightOn = false;
                speak("Flashlight turned off", false);
            } else {
                isFlashlightOn = !isFlashlightOn;
                cameraManager.setTorchMode(cameraId, isFlashlightOn);
                speak("Flashlight " + (isFlashlightOn ? "on" : "off"), false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Flashlight error", e);
            speak("Error controlling flashlight", false);
        }
    }

    private void handleSocialMessaging(String command) {
        String lower = command.toLowerCase();
        String messageBody = "";
        if (lower.contains("saying") || lower.contains("that") || lower.contains("message")) {
            // Basic extraction logic
            if (lower.contains("saying")) messageBody = command.substring(lower.indexOf("saying") + 6).trim();
            else if (lower.contains("that")) messageBody = command.substring(lower.indexOf("that") + 4).trim();
            else messageBody = command;
        } else {
            messageBody = command;
        }

        speak("Choose an app to send your message: " + messageBody, false);
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, messageBody);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Send Message via:");
        startActivity(shareIntent);
    }

    private void handlePlayStore(String command) {
        String query = command.replace("play store", "").replace("google play", "").replace("search", "").trim();
        if (query.isEmpty()) {
            speak("Opening Play Store", false);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q="));
            startActivity(intent);
        } else {
            speak("Searching Play Store for " + query, false);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + query));
            startActivity(intent);
        }
    }

    private void handleOpenApp(String appName) {
        if (appName.isEmpty()) {
            speak("Which app should I open?", true);
            return;
        }

        if (appName.contains("setting")) {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            speak("Opening Settings", false);
            return;
        }
        if (appName.contains("gallery") || appName.contains("photo")) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setType("image/*");
            startActivity(i);
            speak("Opening Gallery", false);
            return;
        }

        PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (android.content.pm.ApplicationInfo packageInfo : packages) {
            String label = pm.getApplicationLabel(packageInfo).toString().toLowerCase();
            if (label.contains(appName)) {
                Intent launchIntent = pm.getLaunchIntentForPackage(packageInfo.packageName);
                if (launchIntent != null) {
                    speak("Opening " + label, false);
                    startActivity(launchIntent);
                    return;
                }
            }
        }
        speak("I couldn't find an app named " + appName, true);
    }

    private void handleMusicAndVideo(String command) {
        if (command.contains("youtube")) {
            speak("Opening YouTube", false);
            launchAppByPackage("com.google.android.youtube");
        } else if (command.contains("spotify")) {
            speak("Opening Spotify", false);
            launchAppByPackage("com.spotify.music");
        } else {
            speak("Playing music", false);
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_MUSIC);
            startActivity(intent);
        }
    }

    private void launchAppByPackage(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) {
            startActivity(intent);
        } else {
            speak("App not installed. I'll search for it.", true);
            Intent webIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + packageName));
            startActivity(webIntent);
        }
    }

    private void saveNote(String content) {
        if (content.isEmpty()) {
            speak("What should it say?", true);
            return;
        }
        noteRepository.insertNote(content, s -> {
            if (s) mainHandler.post(() -> speak("Saved.", false));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initSpeech();
        }
    }
}