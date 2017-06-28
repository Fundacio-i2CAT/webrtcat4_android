package net.i2cat.seg.webrtcat4.sampleapp.activity;

import android.app.ListFragment;
import android.content.Context;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import net.i2cat.seg.webrtcat4.sampleapp.util.AndroidUtils;
import net.i2cat.seg.webrtcat4.sampleapp.util.JSONUtils;
import net.i2cat.seg.webrtcat4.sampleapp.Contact;
import net.i2cat.seg.webrtcat4.sampleapp.R;
import net.i2cat.seg.webrtcat4.sampleapp.WebRTCatUserManager;
import net.i2cat.seg.webrtcat4.sampleapp.util.WebRTConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ContactListFragment extends ListFragment {
    private boolean isLoading;
    private View loadingWidget;
    private View listWidget;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contact_list, container, false);
        loadingWidget = view.findViewById(R.id.loading_widget);
        listWidget = view.findViewById(R.id.list_widget);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        populateContactList();
    }

    private void setIsLoading(boolean isLoading){
        this.isLoading = isLoading;
        loadingWidget.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        listWidget.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    public void populateContactList() {
        if (isLoading) {
            // Already loading the contact list; don't issue another request.
            return;
        }

        setIsLoading(true);
        AndroidUtils.setTextViewText(loadingWidget, R.id.loading_text, "Loading users from "
                + WebRTConstants.getWebRTCat4UserServiceURL() + "...");

        final MainActivity mainActivity = ((MainActivity)getActivity());
        WebRTCatUserManager userManager = mainActivity.getUserManager();
        final String myUsername = userManager.getSavedUsername();
        userManager.getContactList(new JSONUtils.JSONArrayConsumer() {
            @Override
            public void onJSON(JSONArray arr) {
                List<Contact> contactList = new ArrayList<>(arr.length());
                try {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject contactJson = arr.getJSONObject(i);
                        String username = contactJson.getString("username");
                        if (username.equals(myUsername)) {
                            // Don't include ourselves in the contact list!
                            continue;
                        }
                        // Read the contact's image, if there is one.
                        byte[] imageBytes = null;
                        if (contactJson.has("image")) {
                            String imageBase64 = contactJson.getString("image");
                            imageBytes = Base64.decode(imageBase64, Base64.DEFAULT);
                        }

                        contactList.add(new Contact(i + 1,
                                                    username,
                                                    imageBytes,
                                                    contactJson.getString("notif_token")));
                    }
                } catch (JSONException e) {
                    Log.e(WebRTConstants.LOG_TAG, "Unable to read information of contact", e);
                }

                setIsLoading(false);
                final ContactListAdapter contactListAdapter = new ContactListAdapter(getActivity(), contactList.toArray(new Contact[contactList.size()]));
                setListAdapter(contactListAdapter);
                getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        // For some reason this event is fired when the user clicks on the drawer's header!
                        // So ignore this event if the drawer is actually open.
                        if (!mainActivity.isDrawerOpen()) {
                            mainActivity.onContactSelected(contactListAdapter.getItem(position));
                        }
                    }
                });
            }

            @Override
            public void onError(final String errorMessage) {
                setIsLoading(false);
                AndroidUtils.longToast(getActivity(), errorMessage);
                // Clear list of users.
                setListAdapter(new ContactListAdapter(getActivity(), new Contact[0]));
            }
        });
    }

    class ContactListAdapter extends ArrayAdapter<Contact> {

        public ContactListAdapter(Context context, Contact[] contacts) {
            super(context, R.layout.layout_contact_list_item, (contacts != null) ? contacts : new Contact[0]);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;

            if(convertView == null){
                view = getActivity().getLayoutInflater().inflate(R.layout.layout_contact_list_item, null);
            }

            Contact contact = super.getItem(position);

            AndroidUtils.setTextViewText(view, R.id.contact_name, contact.getName());
            if (contact.getImage() != null) {
                AndroidUtils.setImageViewImage(view, R.id.contact_image, contact.getImage());
            } else {
                AndroidUtils.setImageViewImage(view, R.id.contact_image, R.drawable.contact);
            }
            return view;
        }

        @Override
        public long getItemId(int position) {
            return super.getItem(position).getId();
        }
    }
}
