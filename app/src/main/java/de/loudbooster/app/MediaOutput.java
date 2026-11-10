package de.loudbooster.app;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.util.List;

final class MediaOutput {
    private static final AudioAttributes MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build();

    private MediaOutput() {}

    static AudioDeviceInfo bluetoothDevice(AudioManager audioManager) {
        if (audioManager.getMode() != AudioManager.MODE_NORMAL) {
            return null;
        }

        List<AudioDeviceInfo> devices = audioManager.getAudioDevicesForAttributes(MEDIA);
        if (devices.size() != 1) {
            return null;
        }

        AudioDeviceInfo device = devices.get(0);
        return switch (device.getType()) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> device;
            default -> null;
        };
    }
}
