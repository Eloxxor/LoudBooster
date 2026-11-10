package de.loudbooster.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            updateControls();
            handler.postDelayed(this, 750);
        }
    };

    private AudioManager audioManager;
    private TextView outputLabel;
    private TextView statusLabel;
    private TextView gainLabel;
    private SeekBar slider;
    private Button toggle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);
        setContentView(R.layout.activity_main);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        audioManager = getSystemService(AudioManager.class);

        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            var safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        root.requestApplyInsets();

        outputLabel = findViewById(R.id.output);
        statusLabel = findViewById(R.id.status);
        gainLabel = findViewById(R.id.gain);
        slider = findViewById(R.id.slider);
        toggle = findViewById(R.id.toggle);

        slider.setMax(BoostGain.MAX_STEPS);
        int initialSteps = savedInstanceState == null
                ? BoostGain.INITIAL_STEPS
                : savedInstanceState.getInt("steps", BoostGain.INITIAL_STEPS);
        slider.setProgress(BoostService.state == BoostService.State.ACTIVE
                ? BoostService.activeSteps : initialSteps);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateGainLabel();
                if (fromUser && BoostService.state == BoostService.State.ACTIVE) {
                    BoostService.adjust(MainActivity.this, progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        toggle.setOnClickListener(view -> {
            if (BoostService.state == BoostService.State.ACTIVE) {
                BoostService.stop(this);
            } else if (BoostService.state != BoostService.State.STARTING) {
                BoostService.start(this, slider.getProgress());
                askForNotifications();
            }
            updateControls();
        });
        updateGainLabel();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (BoostService.state == BoostService.State.ACTIVE) {
            slider.setProgress(BoostService.activeSteps);
        }
        refresh.run();
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(refresh);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("steps", slider.getProgress());
        super.onSaveInstanceState(outState);
    }

    private void updateControls() {
        AudioDeviceInfo output = MediaOutput.bluetoothDevice(audioManager);
        setLabel(outputLabel, output == null ? getText(R.string.no_bluetooth)
                : getString(R.string.bluetooth_output, output.getProductName()));

        BoostService.State state = BoostService.state;
        int statusText = switch (state) {
            case ACTIVE -> R.string.status_active;
            case STARTING -> R.string.status_starting;
            case ROUTE_CHANGED -> R.string.status_route_changed;
            case UNAVAILABLE -> R.string.status_unavailable;
            case OFF -> R.string.status_off;
        };
        setLabel(statusLabel, getText(statusText));
        boolean active = state == BoostService.State.ACTIVE;
        boolean starting = state == BoostService.State.STARTING;
        slider.setEnabled(!starting);
        toggle.setEnabled(active || (!starting && output != null));
        setLabel(toggle, getText(active ? R.string.turn_off : R.string.turn_on));
    }

    private void updateGainLabel() {
        String value = BoostService.gainText(this, slider.getProgress());
        gainLabel.setText(value);
        slider.setStateDescription(value);
    }

    private void askForNotifications() {
        var preferences = getPreferences(MODE_PRIVATE);
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !preferences.getBoolean("askedForNotifications", false)) {
            preferences.edit().putBoolean("askedForNotifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && BoostService.state == BoostService.State.ACTIVE) {
            // The original foreground notification may have been hidden until permission was granted.
            BoostService.adjust(this, BoostService.activeSteps);
        }
    }

    private static void setLabel(TextView label, CharSequence text) {
        if (!TextUtils.equals(label.getText(), text)) {
            label.setText(text);
        }
    }
}
