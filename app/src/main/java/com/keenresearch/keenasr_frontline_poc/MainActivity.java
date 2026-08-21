package com.keenresearch.keenasr_frontline_poc;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.keenresearch.keenasr.KASRBundle;
import com.keenresearch.keenasr.KASRDecodingGraph;
import com.keenresearch.keenasr.KASRRecognizer;
import com.keenresearch.keenasr.KASRRecognizerListener;
import com.keenresearch.keenasr.KASRRecognizerTriggerPhraseListener;
import com.keenresearch.keenasr.KASRResponse;
import com.keenresearch.keenasr.KASRResult;
import com.keenresearch.keenasr_frontline_poc.bluetooth.BluetoothStatusReceiver;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

@SuppressLint("StaticFieldLeak") // instance field is cleared in onDestroy, acceptable for this PoC
public class MainActivity extends AppCompatActivity implements KASRRecognizerListener, KASRRecognizerTriggerPhraseListener, BluetoothStatusReceiver.BluetoothHeadphoneListenerCallback {
    protected static final String TAG = MainActivity.class.getSimpleName();
    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;
    private TimerTask levelUpdateTask;
    private Timer levelUpdateTimer;

    private ExecutorService executorService;
    public static MainActivity instance;
    private Boolean micPermissionGranted = false;

    private Button startButton;
    private TextView bluetoothStatusTextView;
    private Spinner appModeSpinner;

    private final String triggerPhraseDecodingGraph = "trigger_phrase_commands";
    private final String decodingGraph = "commands";

    private int appMode;

    private BluetoothStatusReceiver bluetoothStatusReceiver;


    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // we need to make sure audio permission is granted before initializing KeenASR SDK
        requestAudioPermissions();

        if (KASRRecognizer.sharedInstance() == null) {
            Log.i(TAG, "Initializing KeenASR recognizer");
            KASRRecognizer.setLogLevel(KASRRecognizer.KASRRecognizerLogLevel.KASRRecognizerLogLevelDebug);
            Context context = this.getApplication().getApplicationContext();
            initializeASRInBackground(context);
        } else {
            enableUIControls();
        }

        MainActivity.instance = this;

        findViewById(R.id.startListening).setOnClickListener(view -> {
            Log.i(TAG, "Starting to listen...");
            final KASRRecognizer recognizer = KASRRecognizer.sharedInstance();

            levelUpdateTimer = new Timer();
            levelUpdateTask = new TimerTask() {
                public void run() {
//                        Log.i(TAG, "     " + recognizer.getInputLevel());
                }
            };
            levelUpdateTimer.schedule(levelUpdateTask, 0, 80); // ~12 updates/sec

            view.setEnabled(false);
            TextView resultText = findViewById(R.id.resultText);
            resultText.setText("");
            recognizer.startListening();
        });
        setupUI();
        setupBluetooth();

    }

    private void setupBluetooth() {
        bluetoothStatusReceiver = new BluetoothStatusReceiver(this, this);
        bluetoothStatusReceiver.register();
    }

    private void setupUI() {
        bluetoothStatusTextView = findViewById(R.id.bluetoothStatusTextView);
        TextView versionTextView = findViewById(R.id.versionTextView);
        // TODO: use KASRVersion.getVersion() once that class is public in the SDK
        versionTextView.setText("SDK Version: 2.2");
        startButton = findViewById(R.id.startListening);
        startButton.setEnabled(false);
        appModeSpinner = findViewById(R.id.listeningTypeSpinner);
        appModeSpinner.setEnabled(false); // this will be enabled after the SDK is fully initialized

        appModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                KASRRecognizer recognizer = KASRRecognizer.sharedInstance();
                if (recognizer == null) {
                    // Recognizer not yet initialized - this is expected during initial UI setup
                    Log.d(TAG, "Spinner selection ignored - recognizer not yet initialized");
                    return;
                }
                int selectedItemPosition = appModeSpinner.getSelectedItemPosition();
                disableUIControls();
                if (recognizer.getRecognizerState() == KASRRecognizer.KASRRecognizerState.KASRRecognizerStateListening) {
                    Log.i(TAG, "Stopping recognizer so we can change the mode");
                    recognizer.stopListening();
                } // TODO handle also KASRRecognizerStateFinalProcessing

                switch (selectedItemPosition) {
                    case Constants.TRIGGER_PHRASE:
                        if (setupTriggerPhrase()) {
                            recognizer.startListening();
                            appMode = Constants.TRIGGER_PHRASE;
                            // startButton stays disabled because we automatically restart listening
                        }
                        break;
                    case Constants.ALWAYS_ON:
                        if (setupAlwaysOnListeningAndTapToTalk()) {
                            recognizer.startListening();
                            appMode = Constants.ALWAYS_ON;
                            // startButton stays disabled because we automatically restart listening
                        }
                        break;
                    case Constants.TAP_TO_TALK:
                        if (setupAlwaysOnListeningAndTapToTalk()) {
                            // we enable the button here because that's the only way to start the recognition
                            // in other modes above we automatically start listening, so we don't need the start
                            // button
                            final Button startButton = findViewById(R.id.startListening);
                            startButton.setEnabled(true);
                            appMode = Constants.TAP_TO_TALK;
                        }
                        break;
                }
                appModeSpinner.setEnabled(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        findViewById(R.id.helpImageView).setOnClickListener(view ->
                startActivity(new Intent(MainActivity.this, HelpActivity.class)));
    }

    private void enableUIControls() {
        startButton.setEnabled(true);
        appModeSpinner.setEnabled(true);
    }

    private void disableUIControls() {
        startButton.setEnabled(false);
        appModeSpinner.setEnabled(false);
    }


    // these setup methods assume recognizer is not listening!
    private boolean setupTriggerPhrase() {
        Log.i(TAG, "Setting up trigger phrase mode");
        KASRRecognizer recognizer = KASRRecognizer.sharedInstance();
        if (recognizer == null) {
            Log.e(TAG, "Unable to retrieve recognizer while trying to setup trigger phrase mode");
            Toast.makeText(this, "Recognizer not initialized", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (KASRDecodingGraph.decodingGraphWithNameExists(triggerPhraseDecodingGraph, recognizer)) {
            Log.i(TAG, "Reusing existing decoding graph " + triggerPhraseDecodingGraph);
        } else {
            if (!createTriggerPhraseDecodingGraph()) {
                Log.e(TAG, "Failed to create trigger phrase decoding graph");
                Toast.makeText(this, "Failed to create trigger phrase decoding graph", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        Log.i(TAG, "Preparing recognizer with trigger phrase decoding graph: " + triggerPhraseDecodingGraph);
        recognizer.prepareForListeningWithDecodingGraphWithName(triggerPhraseDecodingGraph, false);
        return true;
    }


    private boolean setupAlwaysOnListeningAndTapToTalk() {
        Log.i(TAG, "Setting up always-on listening and tap-to-talk mode");
        KASRRecognizer recognizer = KASRRecognizer.sharedInstance();
        if (recognizer == null) {
            Log.e(TAG, "Unable to retrieve recognizer while trying to setup always-on listening mode");
            Toast.makeText(this, "Recognizer not initialized", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (KASRDecodingGraph.decodingGraphWithNameExists(decodingGraph, recognizer)) {
            Log.i(TAG, "Reusing existing decoding graph " + decodingGraph);
        } else {
            if (!createRegularDecodingGraph()) {
                Log.e(TAG, "Failed to create regular decoding graph");
                Toast.makeText(this, "Failed to create decoding graph", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        recognizer.prepareForListeningWithDecodingGraphWithName(decodingGraph, false);
        return true;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        bluetoothStatusReceiver.unregister();
        if (executorService != null) {
            executorService.shutdown();
        }
    }


    public void onPartialResult(KASRRecognizer recognizer, final KASRResult result) {
        Log.i(TAG, "   Partial result: " + result.getCleanText());

        final TextView resultText = findViewById(R.id.resultText);
        resultText.post(() -> {
            resultText.setTextColor(Color.LTGRAY);
            resultText.setText(result.getCleanText());
        });
    }

    public void onTriggerPhrase(KASRRecognizer recognizer) {
        Log.i(TAG, "*** TRIGGER PHRASE DETECTED! ***");
        runOnUiThread(() -> {
            Toast.makeText(this, "Trigger phrase detected!", Toast.LENGTH_SHORT).show();
            TextView resultText = findViewById(R.id.resultText);
            resultText.setText("Listening...");
            resultText.setTextColor(Color.BLUE);
        });
    }


    public void onFinalResponse(final KASRRecognizer recognizer, final KASRResponse response) {
        KASRResult result = response.getAsrResult();
        Log.i(TAG, "Final result: " + result);
        Log.i(TAG, "Final result JSON: " + result);
        final TextView resultText = findViewById(R.id.resultText);
        Log.i(TAG, "resultText: " + resultText);

        boolean status = resultText.post(() -> {
            Log.i(TAG, "Updating UI after receiving final result");
            resultText.setTextColor(Color.GRAY);
            resultText.setText(result.getCleanText());
            if (appMode == Constants.ALWAYS_ON || appMode == Constants.TRIGGER_PHRASE) {
                Log.i(TAG, "Restarting listening since we are in always-on listening mode");
                recognizer.startListening();
            }
            if (appMode == Constants.TAP_TO_TALK) {
                startButton.setEnabled(true);
            }
        });
        if (!status) {
            Log.w(TAG, "Unable to post runnable to the UI queue");
        }
    }

    private void requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                Log.i(TAG, "Requesting mic permission from the users");
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_LONG).show();
                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);
                Log.i(TAG, "Requesting mic permission from the users");
            }
        } else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Microphone permission has already been granted");
            micPermissionGranted = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_RECORD_AUDIO) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                micPermissionGranted = true;
            } else {
                Toast.makeText(this, "Permissions Denied to record audio. You will have to allow microphone access from the Settings->App->KeenASR->Permissions'", Toast.LENGTH_LONG).show();
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onDeviceConnected() {
        bluetoothStatusTextView.setText("Waiting for audio routing...");
        Log.i(TAG, "BT device connected");
        if (KASRRecognizer.sharedInstance() != null) {
            Log.d(TAG, "Recognizer is already initialized; routing audio to BT headset");
            bluetoothStatusReceiver.routeAudioToBluetoothHeadset();
        } else {
            Log.d(TAG, "Recognizer is not yet initialized; routing audio to BT headset will happen after initialization");
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onAudioRouted() {
        Log.i(TAG, "Routed audio to the headset");
        bluetoothStatusTextView.setText("Bluetooth headset active");

        KASRRecognizer recognizer  = KASRRecognizer.sharedInstance();
        if (recognizer != null) {
            if (recognizer.getRecognizerState()== KASRRecognizer.KASRRecognizerState.KASRRecognizerStateListening) {
                // TODO special handling for KASRRecognizerStateFinalProcessing
                recognizer.stopListening();
                recognizer.deactivateAudioStack();
                recognizer.activateAudioStack();
                if (appMode == Constants.ALWAYS_ON || appMode == Constants.TRIGGER_PHRASE) {
                    Log.i(TAG, "Restarting listening after BT connection since we are in ALWAYS_ON/TRIGGER_PHRASE mode");
                    recognizer.startListening();
                }
            }
        }
    }

    @Override
    public void onAudioRoutingFailed() {
        Log.e(TAG, "Failed to route audio to Bluetooth headset");
        runOnUiThread(() ->
            Toast.makeText(this, "Failed to route audio to Bluetooth headset", Toast.LENGTH_SHORT).show()
        );
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void noDeviceConnected() {
        bluetoothStatusTextView.setText("No bluetooth device connected");
        Log.i(TAG, "BT device has been disconnected");
        KASRRecognizer recognizer  = KASRRecognizer.sharedInstance();
        if (recognizer != null) {
            if (recognizer.getRecognizerState()== KASRRecognizer.KASRRecognizerState.KASRRecognizerStateListening) {
                // TODO special handling for KASRRecognizerStateFinalProcessing
                recognizer.stopListening();
                recognizer.deactivateAudioStack();
                recognizer.activateAudioStack();
                if (appMode == Constants.ALWAYS_ON || appMode == Constants.TRIGGER_PHRASE) {
                    Log.i(TAG, "Restarting listening after BT connection since we are in ALWAYS_ON/TRIGGER_PHRASE mode");
                    recognizer.startListening();
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void bluetoothEnabled() {
        Log.d(TAG, "BT enabled");
        bluetoothStatusTextView.setText("Bluetooth enabled");
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void bluetoothDisabled() {
        Log.d(TAG, "BT disabled");
        bluetoothStatusTextView.setText("Bluetooth disabled");
    }


    @SuppressWarnings("BusyWait") // Polling for permission grant; acceptable for initialization
    private void initializeASRInBackground(final Context context) {
        executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            KASRBundle asrBundle = new KASRBundle(context);
            String asrBundleName = "keenA1m-nnet3chain-en-us";

            String asrBundleRootPath = getApplicationInfo().dataDir;
            String asrBundlePath = asrBundleRootPath + "/" + asrBundleName;
            // only install ASR Bundle from the APK if it has not already been installed
            if (asrBundle.isInstalled(asrBundlePath)) {
                Log.i(TAG, "ASR Bundle already installed");
            } else {
                Log.i(TAG, "Installing ASR Bundle");
                if (! asrBundle.installASRBundle(asrBundleName, asrBundleRootPath)) {
                    Log.e(TAG, "Error occurred when installing ASR bundle");
                    return;
                } else {
                    Log.i(TAG, "Installed ASR Bundle");
                }
            }

            Log.i(TAG, "Waiting for microphone permission to be granted");
            while (!micPermissionGranted) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            Log.i(TAG, "Microphone permission is granted");
            Log.i(TAG, "Initializing with bundle at path: " + asrBundlePath);
            KASRRecognizer.initWithASRBundleAtPath(asrBundlePath, getApplicationContext());

            KASRRecognizer recognizer = KASRRecognizer.sharedInstance();
            if (recognizer != null) {
                // we do this early on since it may take a bit of time and on success onAudioRouted
                // callback with reinit audio stack. Other steps below also take some time so enabling
                // UI controls at the end should not be premature in the scenario where BT headset is
                // already connected when the app starts
                bluetoothStatusReceiver.routeAudioToBluetoothHeadset();

                setupAlwaysOnListeningAndTapToTalk();
                appMode = Constants.TAP_TO_TALK;
                createRegularDecodingGraph();

                recognizer.prepareForListeningWithDecodingGraphWithName(decodingGraph, false);
                runOnUiThread(this::enableUIControls);
            } else {
                Log.e(TAG, "Unable to retrieve recognizer");
            }

            // Post-initialization on UI thread
            runOnUiThread(() -> {
                Log.i(TAG, "Initialized KeenASR in the background");
                KASRRecognizer rec = KASRRecognizer.sharedInstance();
                if (rec != null) {
                    Log.i(TAG, "Adding listeners");
                    rec.addListener(MainActivity.instance);
                    rec.addTriggerPhraseListener(MainActivity.instance);
                    Log.i(TAG, "Setting up VAD parameters");
                    // we set these to relatively short values since the use case is single words or short phrase
                    rec.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutEndSilenceForGoodMatch, 0.7f);
                    rec.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutEndSilenceForAnyMatch, 0.7f);

                    rec.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutMaxDuration, 15.0f);
                    rec.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutForNoSpeech, 5.0f);

                    final Button startBtn = findViewById(R.id.startListening);
                    startBtn.setEnabled(true);
                } else {
                    Log.e(TAG, "Recognizer wasn't initialized properly");
                }
            });
        });
    }

    private static String[] getPhrases() {
        return new String[]{
                "CANCEL", "RESUME", "PRINT", "NEXT", "PREVIOUS", "HELP", "STOP", "START", "CLEAR",
                "HELP", "DONE", "BACK", "PAUSE", "ZERO", "ONE", "TWO", "THREE", "FOUR", "FIVE",
                "SIX", "SEVEN", "EIGHT", "NINE", "O", "PICK", "SKIP"
        };
    }


    private boolean createRegularDecodingGraph() {
        KASRRecognizer recognizer = KASRRecognizer.sharedInstance();
        String[] phrases = MainActivity.getPhrases();
        Log.i(TAG, "Creating regular decoding graph: " + decodingGraph);
        boolean success = KASRDecodingGraph.createDecodingGraphFromPhrases(phrases, recognizer, decodingGraph);
        Log.i(TAG, "Regular decoding graph creation " + (success ? "succeeded" : "failed"));
        return success;
    }

    private boolean createTriggerPhraseDecodingGraph() {
        KASRRecognizer recognizer = KASRRecognizer.sharedInstance();
        String triggerPhrase = "hey computer";
        String[] phrases = MainActivity.getPhrases();
        Log.i(TAG, "Creating trigger phrase decoding graph with trigger: '" + triggerPhrase + "'");
        boolean success = KASRDecodingGraph.createDecodingGraphFromPhrasesWithTriggerPhrase(phrases, null,
                triggerPhrase, recognizer, triggerPhraseDecodingGraph);
        Log.i(TAG, "Trigger phrase decoding graph creation " + (success ? "succeeded" : "failed"));
        return success;
    }

}


