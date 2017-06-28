package net.i2cat.seg.webrtcat4.sampleapp.activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import net.i2cat.seg.webrtcat4.sampleapp.R;

public class UsernamePromptDialogFragment extends DialogFragment {
    private MainActivity mainActivity;
    private String defaultUsername;

    public void setParams(MainActivity mainActivity, String defaultUsername) {
        this.mainActivity = mainActivity;
        this.defaultUsername = defaultUsername;
    }

    @Override
    public void onStart() {
        super.onStart();
        final AlertDialog dlg = (AlertDialog)this.getDialog();

        Button okButton = dlg.getButton(DialogInterface.BUTTON_POSITIVE);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText usernameEdit = (EditText)dlg.findViewById(R.id.username);
                String username = usernameEdit.getText().toString().trim();
                if (username.length() > 0) {
                    mainActivity.onUsernameSpecified(username);
                    dlg.dismiss();
                }
            }
        });
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = mainActivity.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.fragment_username_prompt_dialog, null);

        final EditText usernameEdit = (EditText)dialogView.findViewById(R.id.username);
        usernameEdit.setText(defaultUsername);
        usernameEdit.setSelectAllOnFocus(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(mainActivity);
        builder.setPositiveButton("Accept", null);

        AlertDialog dlg = builder.create();
        // Remove margins around the dialog.
        dlg.setView(dialogView, 0, 0, 0, 0);

        return dlg;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        // If this dialog is cancelled, close the application.
        mainActivity.finish();
    }

}
