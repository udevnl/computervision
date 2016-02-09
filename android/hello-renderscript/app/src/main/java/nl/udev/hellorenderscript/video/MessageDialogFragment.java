package nl.udev.hellorenderscript.video;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

public class MessageDialogFragment extends DialogFragment {

    private static final String ARG_MESSAGE_INT = "message_int";
    private static final String ARG_MESSAGE_STRING = "message_string";

    public static MessageDialogFragment newInstance(int message) {
        MessageDialogFragment fragment = new MessageDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_MESSAGE_INT, message);
        fragment.setArguments(args);
        return fragment;
    }

    public static MessageDialogFragment newInstance(String message) {
        MessageDialogFragment fragment = new MessageDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE_STRING, message);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setPositiveButton(android.R.string.ok, null);
        Bundle args = getArguments();
        if (args.containsKey(ARG_MESSAGE_INT)) {
            builder.setMessage(args.getInt(ARG_MESSAGE_INT));
        } else if (args.containsKey(ARG_MESSAGE_STRING)) {
            builder.setMessage(args.getString(ARG_MESSAGE_STRING));
        }
        return builder.create();
    }

}