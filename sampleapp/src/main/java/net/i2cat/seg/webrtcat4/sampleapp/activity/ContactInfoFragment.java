package net.i2cat.seg.webrtcat4.sampleapp.activity;


import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import net.i2cat.seg.webrtcat4.sampleapp.util.AndroidUtils;
import net.i2cat.seg.webrtcat4.sampleapp.Contact;
import net.i2cat.seg.webrtcat4.sampleapp.R;

public class ContactInfoFragment extends Fragment {
    private Contact contact;

    public ContactInfoFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contact_info, container, false);

        final MainActivity mainActivity = (MainActivity)getActivity();
        ImageButton callButton = (ImageButton)view.findViewById(R.id.call_button);
        callButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mainActivity.onCallContact(contact);
            }
        });

        ImageButton backButton = (ImageButton)view.findViewById(R.id.back_button);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mainActivity.onBackToContactListPressed();
            }
        });

        return view;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (contact.getImage() != null) {
            AndroidUtils.setImageViewImage(getActivity(), R.id.contact_image, contact.getImage());
        } else {
            AndroidUtils.setImageViewImage(getActivity(), R.id.contact_image, R.drawable.contact);
        }
        AndroidUtils.setTextViewText(getActivity(), R.id.contact_name, contact.getName());
    }
}
