package jackpal.androidterm;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

// AndroidCompat.SDK >= Build.VERSION_CODES.LOLLIPOP
//   final ProgressRingDialog pd = ProgressRingDialog.create(null, activity.getString(R.string.update_message), false);
//   pd.show(((AppCompatActivity) activity).getSupportFragmentManager(), "PROGRESSBAR_DIALOG_INSTALL");
//   ....
//   if (pd != null && pd.getShowsDialog()) {
//       pd.dismiss();
//   }

class ProgressRingDialog extends DialogFragment {

    public ProgressRingDialog() {
    }

    private Dialog mDialog;
    private CharSequence mTitle = "Task in progress";
    private CharSequence mMessage = "Please wait for while.";
    private boolean mCancelable = false;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mDialog = new Dialog(getActivity());
        mDialog.setContentView(R.layout.dialog_progress);
        mDialog.getLayoutInflater();
        mDialog.setCancelable(mCancelable);
        mDialog.setCanceledOnTouchOutside(mCancelable);
        mDialog.setTitle(mTitle);
        setMessage(mMessage.toString());
        return mDialog;
    }

    static ProgressRingDialog newInstance() {
        return new ProgressRingDialog();
    }

    static ProgressRingDialog create(CharSequence title, CharSequence message, boolean cancelable) {
        ProgressRingDialog prd = new ProgressRingDialog();
        prd.setTitle(title);
        prd.setMessage(message);
        prd.setCancelable(cancelable);
        prd.setCanceledOnTouchOutside(cancelable);
        return prd;
    }

    void setCanceledOnTouchOutside(boolean cancelable) {
        try {
            mDialog.setCanceledOnTouchOutside(cancelable);
        } catch (Exception e) {
            // Do nothing
        }
    }

    void setTitle(CharSequence title) {
        try {
            mTitle = title;
            mDialog.setTitle(title);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void setMessage(CharSequence mes) {
        try {
            mMessage = mes;
            TextView view = mDialog.findViewById(R.id.progress_textview);
            view.setText(mes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
