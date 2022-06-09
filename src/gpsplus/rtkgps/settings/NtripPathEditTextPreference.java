package gpsplus.rtkgps.settings;

import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;

import android.util.AttributeSet;
import android.text.Editable;
import android.text.TextWatcher;
import android.app.AlertDialog;
import android.app.Dialog;
import android.widget.Button;

public class NtripPathEditTextPreference extends EditTextPreference {

    public NtripPathEditTextPreference(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
    }

    public NtripPathEditTextPreference(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
    }

    private class EditTextWatcher implements TextWatcher {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void beforeTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            onEditTextChanged();
        }
    }

    EditTextWatcher m_watcher = new EditTextWatcher();

    protected boolean onCheckValue(String value) {
        return null != SettingsHelper.decodeNtripTcpPath(value);
    }

    protected void onEditTextChanged() {
        boolean enable = onCheckValue(getEditText().getText().toString());
        Dialog dlg = getDialog();
        if (dlg instanceof AlertDialog) {
            AlertDialog alertDlg = (AlertDialog) dlg;
            Button btn = alertDlg.getButton(AlertDialog.BUTTON_POSITIVE);
            btn.setEnabled(enable);
        }
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        getEditText().removeTextChangedListener(m_watcher);
        getEditText().addTextChangedListener(m_watcher);
        onEditTextChanged();
    }
}
