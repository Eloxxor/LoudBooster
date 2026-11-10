package de.loudbooster.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.text.NumberFormat;

public final class BoostService extends Service {
    enum State { OFF, STARTING, ACTIVE, ROUTE_CHANGED, UNAVAILABLE }

    private static final String TAG = "LoudBooster";
    private static final String CHANNEL_ID = "loudness";
    private static final int NOTIFICATION_ID = 1;
    private static final String START = "start";
    private static final String ADJUST = "adjust";
    private static final String STOP = "stop";
    private static final String STEPS = "steps";

    // Service and activity callbacks share Android's main thread.
    static State state = State.OFF;
    static int activeSteps;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable checkOutput = this::checkOutputAndEffect;
    private final AudioManager.OnModeChangedListener modeChanges = mode -> checkOutputAndEffect();
    private final AudioDeviceCallback audioDevices = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            checkOutputAndEffect();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            if (effect != null) {
                for (AudioDeviceInfo device : removed) {
                    if (device.getId() == outputId) {
                        finish(State.ROUTE_CHANGED);
                        return;
                    }
                }
            }
            checkOutputAndEffect();
        }
    };
    private final BroadcastReceiver noisyOutput = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (effect != null) {
                finish(State.ROUTE_CHANGED);
            }
        }
    };

    private AudioManager audioManager;
    private NotificationManager notifications;
    private LoudnessEnhancer effect;
    private int outputId;

    static void start(Context context, int steps) {
        state = State.STARTING;
        try {
            context.startForegroundService(command(context, START).putExtra(STEPS, steps));
        } catch (IllegalStateException | SecurityException e) {
            Log.w(TAG, "Could not start booster", e);
            state = State.UNAVAILABLE;
        }
    }

    static void adjust(Context context, int steps) {
        context.startService(command(context, ADJUST).putExtra(STEPS, steps));
    }

    static void stop(Context context) {
        context.startService(command(context, STOP));
    }

    private static Intent command(Context context, String action) {
        return new Intent(context, BoostService.class).setAction(action);
    }

    static String gainText(Context context, int steps) {
        String gain = NumberFormat.getNumberInstance().format(BoostGain.toMillibels(steps) / 100.0);
        return context.getString(steps == 0 ? R.string.gain_zero : R.string.gain_positive, gain);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = getSystemService(AudioManager.class);
        notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW));
        audioManager.registerAudioDeviceCallback(audioDevices, handler);
        audioManager.addOnModeChangedListener(getMainExecutor(), modeChanges);
        registerReceiver(noisyOutput, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        int steps = intent == null ? 0 : intent.getIntExtra(STEPS, 0);
        if (START.equals(action)) {
            enable(steps);
        } else if (ADJUST.equals(action) && effect != null) {
            checkOutputAndEffect();
            if (effect == null) {
                return START_NOT_STICKY;
            }
            try {
                applyGain(steps);
                notifications.notify(NOTIFICATION_ID, notification(steps));
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not change boost", e);
                finish(State.UNAVAILABLE);
            }
        } else if (STOP.equals(action)) {
            finish(State.OFF);
        } else if (effect == null) {
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    private void enable(int steps) {
        try {
            int type = Build.VERSION.SDK_INT >= 34
                    ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    : ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE;
            startForeground(NOTIFICATION_ID, notification(steps), type);
            AudioDeviceInfo output = MediaOutput.bluetoothDevice(audioManager);
            if (output == null) {
                finish(State.ROUTE_CHANGED);
                return;
            }

            if (effect == null) {
                outputId = output.getId();
                // Session 0 targets Android's output mix when the device supports it.
                effect = new LoudnessEnhancer(0);
            }
            applyGain(steps);
            if (effect.setEnabled(true) != AudioEffect.SUCCESS || !effect.getEnabled()) {
                throw new IllegalStateException("Audio effect could not be enabled");
            }
            state = State.ACTIVE;
            checkOutputAndEffect();
        } catch (RuntimeException e) {
            Log.w(TAG, "Audio effect unavailable", e);
            finish(State.UNAVAILABLE);
        }
    }

    private void applyGain(int steps) {
        if (!effect.hasControl()) {
            throw new IllegalStateException("Another app controls the audio effect");
        }
        effect.setTargetGain(BoostGain.toMillibels(steps));
        activeSteps = steps;
    }

    private void checkOutputAndEffect() {
        handler.removeCallbacks(checkOutput);
        if (effect == null) {
            return;
        }
        try {
            AudioDeviceInfo output = MediaOutput.bluetoothDevice(audioManager);
            if (output == null || output.getId() != outputId) {
                finish(State.ROUTE_CHANGED);
            } else if (!effect.hasControl() || !effect.getEnabled()) {
                finish(State.UNAVAILABLE);
            } else {
                // Route changes can also happen without adding or removing a device.
                handler.postDelayed(checkOutput, 750);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not check audio output", e);
            finish(State.UNAVAILABLE);
        }
    }

    private Notification notification(int steps) {
        int pendingFlags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), pendingFlags);
        PendingIntent stop = PendingIntent.getService(this, 0, command(this, STOP), pendingFlags);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_wave)
                .setContentTitle(getString(R.string.notification_title, gainText(this, steps)))
                .setContentIntent(open)
                .setDeleteIntent(stop)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setShowWhen(false)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(new Notification.Action.Builder(null, getString(R.string.turn_off), stop).build())
                .build();
    }

    private void finish(State reason) {
        releaseEffect();
        state = reason;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releaseEffect() {
        handler.removeCallbacks(checkOutput);
        if (effect == null) {
            return;
        }
        try {
            if (effect.hasControl()) {
                effect.setEnabled(false);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Audio effect already disconnected", e);
        } finally {
            effect.release();
            effect = null;
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(noisyOutput);
        audioManager.unregisterAudioDeviceCallback(audioDevices);
        audioManager.removeOnModeChangedListener(modeChanges);
        releaseEffect();
        if (state == State.ACTIVE || state == State.STARTING) {
            state = State.OFF;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
