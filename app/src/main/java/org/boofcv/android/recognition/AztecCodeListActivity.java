package org.boofcv.android.recognition;

import static org.boofcv.android.recognition.QrCodeDetectActivity.uniqueLock;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.boofcv.android.R;

import java.util.ArrayList;
import java.util.List;

import boofcv.alg.fiducial.aztec.AztecCode;

/**
 * Presents a list of detected QR Codes. Can select each one to get more info and copy the message
 */
public class AztecCodeListActivity extends AppCompatActivity {

    // List of all qr codes found in the order they were added to the listview
    List<AztecCode> markers = new ArrayList<>();

    TextView textMessage;
    ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.qrcode_list);

        // Create the list of QR Codes
        listView = findViewById(R.id.list_view);
        ArrayList<String> listItems=new ArrayList<>();
        synchronized (uniqueLock) {
            for (AztecCode az : AztecCodeDetectActivity.unique.values()) {
                markers.add(az);
                // filter out bad characters and new lines
                String message = az.message.replaceAll("\\p{C}", " ");
                int N = Math.min(25,message.length());
                listItems.add(String.format("%4d: %25s",az.message.length(),message.substring(0,N)));
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
            AztecCode az = markers.get(position);
            textVersion.setText(""+az.dataLayers);
            textError.setText(String.format("%.2f", az.getCorrectionLevel()));
            textMask.setText("N/A");
            textMode.setText("N/A");
            textMessage.setText(""+az.message);
        });

        if( QrCodeDetectActivity.selectedQR != null ) {
            moveToSelected(QrCodeDetectActivity.selectedQR);
        }
    }

    private void moveToSelected( String target ) {
        int matched = -1;
        for (int i = 0; i < markers.size(); i++) {
            if( markers.get(i).message.equals(target)) {
                matched = i;
                break;
            }
        }

        if( matched != -1 ) {
            listView.smoothScrollToPosition(matched);
            listView.performItemClick(listView.getChildAt(matched),matched,
                    listView.getItemIdAtPosition(matched));
        }
    }

    public void pressedCopyMessage( View view ) {
        CharSequence message = textMessage.getText();
        if( message.length() > 0 ) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("aztec", message);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied "+message.length()+" characters", Toast.LENGTH_SHORT).show();
        }
    }

    public void pressedClearList( View view ) {
        synchronized (uniqueLock) {
            QrCodeDetectActivity.unique.clear();
        }
        recreate();
    }

}
