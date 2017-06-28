package net.i2cat.seg.webrtcat4.sampleapp.activity;

import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.i2cat.seg.webrtcat4.sampleapp.BuildConfig;
import net.i2cat.seg.webrtcat4.sampleapp.util.AndroidUtils;
import net.i2cat.seg.webrtcat4.sampleapp.R;
import net.i2cat.seg.webrtcat4.sampleapp.WebRTCatUserManager;
import net.i2cat.seg.webrtcat4.sampleapp.util.WebRTConstants;

public class AboutWebRTCatFragment extends Fragment {
    public AboutWebRTCatFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about, container, false);

        WebRTCatUserManager userManager = ((MainActivity)getActivity()).getUserManager();
        final String myUsername = userManager.getSavedUsername();

        StringBuilder moreInfoText = new StringBuilder();
        appendInfo(moreInfoText, "App Version", BuildConfig.VERSION_NAME);
        appendInfo(moreInfoText, "My Username", myUsername);
        appendInfo(moreInfoText, "Room server", WebRTConstants.getWebRTCat4ServerURL());
        appendInfo(moreInfoText, "libjingle version", WebRTConstants.LIBJINGLE_VERSION);
        AndroidUtils.setTextViewText(view, R.id.more_info, moreInfoText.toString());

        return view;
    }

    private void appendInfo(StringBuilder moreInfoText, String name, String value) {
        moreInfoText.append(name).append(": ").append(value).append("\n\n");
    }
}
