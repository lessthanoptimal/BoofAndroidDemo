package org.boofcv.android.recognition;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.boofcv.android.R;

import java.util.ArrayList;
import java.util.List;

import boofcv.alg.fiducial.qrcode.QrCode;

import static org.boofcv.android.recognition.QrCodeDetectActivity.uniqueLock;

/**
 * Presents a list of detected QR Codes. Can select each one to get more info and copy the message
 */
public class QrCodeListActivity extends AppCompatActivity {

    // List of all qr codes found in the order they were added to the listview
    List<QrCode> qrcodes = new ArrayList<>();

    TextView textMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.qrcode_list);

        // Create the list of QR Codes
        final ListView listView = findViewById(R.id.list_view);
        ArrayList<String> listItems=new ArrayList<>();
        synchronized (uniqueLock) {
            for (QrCode qr : QrCodeDetectActivity.unique.values()) {
                qrcodes.add(qr);
                listItems.add("Detected: " + qr.message);
            }
        }

        final ArrayAdapter<String> adapter = new ArrayAdapter<String>
                (this, android.R.layout.simple_list_item_1, listItems);
        listView.setAdapter(adapter);

        TextView textVersion = findViewById(R.id.text_version);
        TextView textError = findViewById(R.id.text_error);
        TextView textMask = findViewById(R.id.text_mask);
        TextView textMode = findViewById(R.id.text_mode);
        textMessage = findViewById(R.id.text_message);

        listView.setClickable(true);
        listView.setOnItemClickListener((adapterView, view, position, l) -> {
            QrCode qr = qrcodes.get(position);
            textVersion.setText(""+qr.version);
            textError.setText(""+qr.error);
            textMask.setText(""+qr.mask);
            textMode.setText(""+qr.mode);
            textMessage.setText(""+qr.message);
        });
    }

    public void pressedCopyMessage( View view ) {
        CharSequence message = textMessage.getText();
        if( message.length() > 0 ) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("qrcode", message);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied "+message.length()+" characters", Toast.LENGTH_SHORT).show();
        }
    }

}
