package net.i2cat.seg.webrtcat4.sampleapp;


import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.i2cat.seg.webrtcat4.sampleapp.activity.WebRTCallActivity;

public class WebRTCallControlFragment extends Fragment {

    private TextView contactView;
    private TextView callInfoText;
    private WebRTCallControlEvents callControlEvents;

    /**
     * Call control interface for container activity.
     */
    public interface WebRTCallControlEvents {
        void onCallHangUp();
        void onCameraSwitch();
        boolean onToggleAudioMute();
    }

    public void setCallControlEventsHandler(WebRTCallControlEvents callControlEvents) {
        this.callControlEvents = callControlEvents;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View controlView = inflater.inflate(R.layout.fragment_webrtcallcontrol, container, false);

        // Create UI controls.
        contactView = (TextView) controlView.findViewById(R.id.contact_name_call);
        View disconnectButton = controlView.findViewById(R.id.button_call_disconnect);
        View cameraSwitchButton = controlView.findViewById(R.id.button_call_switch_camera);
        final View toggleMuteButton = controlView.findViewById(R.id.button_call_toggle_mute);
        View toggleCallInfoButton = controlView.findViewById(R.id.button_call_info);
        callInfoText = (TextView)controlView.findViewById(R.id.text_call_info);

        // Add buttons click events.
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                callControlEvents.onCallHangUp();
            }
        });

        cameraSwitchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                callControlEvents.onCameraSwitch();
            }
        });

        toggleMuteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean newState = callControlEvents.onToggleAudioMute();
                // Log.d(WebRTConstants.LOG_TAG, "newState: " + newState);
                toggleMuteButton.setBackgroundResource(newState ? R.drawable.mic_on : R.drawable.mic_off);
            }
        });

        toggleCallInfoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int visibility = callInfoText.getVisibility();
                if (visibility == View.VISIBLE) {
                    visibility = View.INVISIBLE;
                } else {
                    visibility = View.VISIBLE;
                }
                callInfoText.setVisibility(visibility);
            }
        });

        return controlView;
    }

    public void setLatestCallInfo(final String latestCallInfo) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callInfoText.setText(latestCallInfo);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        Bundle args = getArguments();
        if (args != null) {
            String contactName = args.getString(WebRTCallActivity.WEBRTCAT_PEER_NAME);
            if (contactName != null) {
                contactView.setText(contactName);
            }
        }
    }
}
