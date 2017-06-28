package net.i2cat.seg.webrtcat4.sampleapp.activity;

import android.app.Fragment;
import android.content.Context;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.TextView;

import net.i2cat.seg.webrtcat4.sampleapp.util.AndroidUtils;
import net.i2cat.seg.webrtcat4.sampleapp.R;
import net.i2cat.seg.webrtcat4.sampleapp.util.WebRTConstants;

public class IncomingCallFragment extends Fragment {
    private String callerName;
    private Vibrator vibrator;
    private AudioManager audioManager;
    private Ringtone currentRingtone;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_incoming_call, container, false);

        vibrator = (Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        Uri currentRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(getActivity().getApplicationContext(), RingtoneManager.TYPE_RINGTONE);
        if (currentRingtoneUri != null) {
            currentRingtone = RingtoneManager.getRingtone(getActivity(), currentRingtoneUri);
        }

        audioManager = (AudioManager)getActivity().getSystemService(Context.AUDIO_SERVICE);

        startIncomingCallEffects(view);

        final WebRTCallActivity webRTCallActivity = ((WebRTCallActivity)getActivity());
        ImageButton acceptButton = (ImageButton)view.findViewById(R.id.accept_button);
        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopIncomingCallEffects();
                webRTCallActivity.onAcceptCall();
            }
        });

        ImageButton rejectButton = (ImageButton)view.findViewById(R.id.reject_button);
        rejectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopIncomingCallEffects();
                webRTCallActivity.onRejectCall();
            }
        });

        return view;
    }

    public void setCallerName(String callerName) {
        this.callerName = callerName;
    }

    public void setRingtoneVolume(boolean up) {
        if (audioManager == null) {
            return;
        }
        int direction = (up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER);
        audioManager.adjustStreamVolume(AudioManager.STREAM_RING, direction, AudioManager.FLAG_SHOW_UI);

        playRingtoneOrVibrate();
    }

    @Override
    public void onDetach() {
        stopIncomingCallEffects();
        super.onDetach();
    }

    private void startIncomingCallEffects(View view) {
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(200); //You can manage the blinking time with this parameter
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        TextView incomingCallText = (TextView)view.findViewById(R.id.incoming_call_text);
        incomingCallText.startAnimation(anim);
        AndroidUtils.setTextViewText(view, R.id.caller_name, callerName);

        playRingtoneOrVibrate();
    }

    private void playRingtoneOrVibrate() {
        int ringVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        if (ringVolume == 0) {
            stopRingtone();
            startVibrations();
        } else {
            stopVibrations();
            playRingtone();
        }
    }

    private void stopIncomingCallEffects() {
        stopRingtone();
        stopVibrations();
    }

    private void playRingtone() {
        if ((currentRingtone != null) && (!currentRingtone.isPlaying())) {
            currentRingtone.play();
        }
    }

    private void stopRingtone() {
        if (currentRingtone != null) {
            currentRingtone.stop();
        }
    }

    private void startVibrations() {
        if ((vibrator == null) || (!vibrator.hasVibrator())) {
            Log.w(WebRTConstants.LOG_TAG, "Vibrator unavailable.");
        } else {
            // TODO use system-defined vibration patterns?
            long[] vibrationPattern = { 100, 800, 450, 800, 450 };
            vibrator.vibrate(vibrationPattern, 0);
        }
    }

    private void stopVibrations() {
        if (vibrator != null) {
            vibrator.cancel();
        }
    }
}
