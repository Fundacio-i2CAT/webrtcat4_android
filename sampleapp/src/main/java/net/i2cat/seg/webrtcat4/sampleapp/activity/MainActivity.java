package net.i2cat.seg.webrtcat4.sampleapp.activity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;

import net.i2cat.seg.webrtcat4.sampleapp.util.AndroidUtils;
import net.i2cat.seg.webrtcat4.sampleapp.Contact;
import net.i2cat.seg.webrtcat4.sampleapp.R;
import net.i2cat.seg.webrtcat4.sampleapp.WebRTCatUserManager;
import net.i2cat.seg.webrtcat4.sampleapp.util.WebRTConstants;

import java.util.ArrayList;

/**
 * Using AppCompatActivity in order to use the v7 support library Toolbar as an action bar.
 */
public class MainActivity extends AppCompatActivity {
    // Runtime permissions for Android 23 (Marshmallow)
    private static final String[] MY_PERMISSIONS = new String[] {
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.VIBRATE
    };
    private static final int PERMISSIONS_REQUEST_CODE = 1;

    private ListView burgerList;
    private RelativeLayout burgerMenu;
    private ActionBarDrawerToggle burgerMenuToggle;
    private DrawerLayout drawerLayout;

    private ArrayList<BurgerItem> burgerItems = new ArrayList<BurgerItem>();

    private ContactListFragment contactListFragment;
    private ContactInfoFragment contactInfoFragment;
    private Fragment aboutFragment;
    private Fragment currentFragment;

    private WebRTCatUserManager userManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WebRTConstants.initProperties(this);

        userManager = new WebRTCatUserManager(this);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
        burgerMenu = (RelativeLayout) findViewById(R.id.burgerMenu);
        burgerList = (ListView) findViewById(R.id.burgerItems);

        contactListFragment = new ContactListFragment();
        contactInfoFragment = new ContactInfoFragment();
        aboutFragment = new AboutWebRTCatFragment();

        setupWindowFlags();
        setupBurgerMenu();
        setupActionBar();

        if (!hasPermissions(this)) {
            ActivityCompat.requestPermissions(this, MY_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
        }

        if (!hasWriteSettingsPermission(this)) {
            // There is a bug with Android 23 that requires us to request the WRITE_SETTINGS permission this way.
            // See http://stackoverflow.com/a/33509180.
            Intent goToSettings = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            goToSettings.setData(Uri.parse("package:" + getPackageName()));
            startActivity(goToSettings);
        }

        final String username = userManager.getSavedUsername();
        if (username == null) {
            // Prompt username for this device.
            final String defaultUsername = Build.MODEL.replaceAll("\\W+", "").toLowerCase() + AndroidUtils.generateRandomNumeric(5);
            Log.d(WebRTConstants.LOG_TAG, "Using defaultUsername: " + defaultUsername);
            UsernamePromptDialogFragment unamePrompt = new UsernamePromptDialogFragment();
            unamePrompt.setParams(this, defaultUsername);
            unamePrompt.show(getFragmentManager(), "usernamePrompt");
        } else {
            Log.d(WebRTConstants.LOG_TAG, "Using stored username: " + username);
            showDefaultFragment();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentFragment == contactListFragment) {
            super.onBackPressed();
        } else {
            replaceMainContent(contactListFragment);
        }
    }

    public void onUsernameSpecified(String username) {
        userManager.saveUsername(username);
        showDefaultFragment();
    }

    public void onContactSelected(Contact contact) {
        contactInfoFragment.setContact(contact);
        replaceMainContent(contactInfoFragment);
    }

    public void onCallContact(Contact contact) {
        if (hasPermissions(this) && hasWriteSettingsPermission(this)) {
            String roomId = generateRoomId(contact);
            WebRTCallActivity.startOutgoingCall(this, roomId, userManager.getSavedUsername(),
                                                contact.getName(), contact.getNotificationToken());
        } else {
            AndroidUtils.longToast(this, "Not all permissions were granted; can't perform calls.\n");
        }
    }

    public void onBackToContactListPressed() {
        replaceMainContent(contactListFragment);
    }

    public WebRTCatUserManager getUserManager() {
        return userManager;
    }

    private void setupWindowFlags() {
        // Flags to ensure that the activity is shown even when the device is in standby.
        // See https://newfivefour.com/android-waking-screen-dismiss-keyguard.html
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public static boolean hasPermissions(Context ctx) {
        boolean hasPermissions = true;
        for (String permission : MY_PERMISSIONS) {
            if ((ActivityCompat.checkSelfPermission(ctx, permission) != PackageManager.PERMISSION_GRANTED) &&
                !Manifest.permission.CHANGE_NETWORK_STATE.equals(permission)) {
                hasPermissions = false;
            }
        }
        return hasPermissions;
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static boolean hasWriteSettingsPermission(Context ctx) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            boolean hasCNSPerm = (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.CHANGE_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED);
            boolean canWrite = Settings.System.canWrite(ctx);
            return hasCNSPerm || canWrite;
        }
        return true;
    }

    private void showDefaultFragment() {
        AndroidUtils.setTextViewText(this, R.id.username, userManager.getSavedUsername());
        replaceMainContent(contactListFragment);
    }

    private void setupBurgerMenu() {
        burgerItems.add(new BurgerItem(getString(R.string.menu_contacts), getString(R.string.menu_contacts_hint)));
        burgerItems.add(new BurgerItem(getString(R.string.menu_about), getString(R.string.menu_about_hint)));

        DrawerListAdapter adapter = new DrawerListAdapter(this, burgerItems);
        burgerList.setAdapter(adapter);

        burgerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onItemClicked(view, position);
            }
        });

        burgerMenuToggle = new ActionBarDrawerToggle(this, drawerLayout, 0, 0);
        drawerLayout.addDrawerListener(burgerMenuToggle);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle
        // If it returns true, then it has handled
        // the nav drawer indicator touch event
        if (burgerMenuToggle.onOptionsItemSelected(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public boolean isDrawerOpen() {
        return drawerLayout.isDrawerOpen(GravityCompat.START);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        burgerMenuToggle.syncState();
    }

    private void setupActionBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void onItemClicked(View view, int position) {
        Fragment fragment = contactListFragment;

        if (position == 0) {
            if (contactListFragment.isAdded()) {
                Log.d(WebRTConstants.LOG_TAG, "Repopulating contact list");
                contactListFragment.populateContactList();
            }
        } else if (position == 1) {
            fragment = aboutFragment;
        }

        replaceMainContent(fragment);

        burgerList.setItemChecked(position, true);
        drawerLayout.closeDrawer(burgerMenu);
    }

    private void replaceMainContent(Fragment fragment) {
        currentFragment = fragment;
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.mainContent, fragment).commit();
    }

    private String generateRoomId(Contact contact) {
        String myUsername = userManager.getSavedUsername();
        return myUsername + "-" + contact.getName() + "-" + AndroidUtils.generateRandomNumeric(6);
    }

    static class DrawerListAdapter extends BaseAdapter {

        Context context;
        ArrayList<BurgerItem> burgerItems;

        public DrawerListAdapter(Context context, ArrayList<BurgerItem> burgerItems) {
            this.context = context;
            this.burgerItems = burgerItems;
        }

        @Override
        public int getCount() {
            return burgerItems.size();
        }

        @Override
        public Object getItem(int position) {
            return burgerItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.layout_burger_item, null);
            } else {
                view = convertView;
            }

            AndroidUtils.setTextViewText(view, R.id.menu_item, burgerItems.get(position).getTitle());
            AndroidUtils.setTextViewText(view, R.id.menu_item_hint, burgerItems.get(position).getSubtitle());

            return view;
        }
    }

    static class BurgerItem {
        String title;
        String subtitle;

        public BurgerItem(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getTitle() {
            return title;
        }
    }
}
