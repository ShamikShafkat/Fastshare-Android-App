package com.example.fastshare;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class sendActivity extends AppCompatActivity {

    TextView ipAddress,portNumber,filePath,fileName;
    Button media_btn,dow_btn,music_btn,send_btn;
    private sendActivity activity;

    private static final int SELECT_PICTURE = 1;
    private static final int SELECT_FILE = 2;
    private static final int SELECT_MUSIC = 3;
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1;
    private String selectedImagePath;
    private String selectedImageName;

    private String selectedFilePath;
    private String selectedFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);
        activity = this;

        if (!hasReadExternalStoragePermission()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            doWork();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                doWork();
            } else {
                sendActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(sendActivity.this,
                                "Permission Denied",
                                Toast.LENGTH_LONG).show();
                    }});
            }
        }
    }

    public void doWork(){
        ipAddress =findViewById(R.id.ipAddress);
        portNumber = findViewById(R.id.portNumber);
        media_btn = findViewById(R.id.media_btn);
        dow_btn = findViewById(R.id.dow_btn);
        music_btn = findViewById(R.id.music_btn);
        send_btn = findViewById(R.id.send_btn);
        filePath = findViewById(R.id.filePath);
        fileName = findViewById(R.id.fileName);

        media_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent pickImageIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(pickImageIntent,SELECT_PICTURE);
            }
        });

        dow_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent pickFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
                pickFileIntent.setType("*/*");
                startActivityForResult(pickFileIntent, SELECT_FILE);
            }
        });

        send_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SenderRxThread senderRxThread = new SenderRxThread(ipAddress.getText().toString(),Integer.parseInt(portNumber.getText().toString()),activity);
                senderRxThread.start();
            }
        });
    }

    private boolean hasReadExternalStoragePermission() {
        return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        filePath.setText("Here i am");
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                selectedImagePath = getImagePath(selectedImageUri);
                selectedImageName = getImageFileName(selectedImageUri);
                filePath.setText(selectedImagePath);
                fileName.setText(selectedImageName);
            }
            else if(requestCode==SELECT_FILE)
            {
                Uri selectedFileUri = data.getData();
                selectedFilePath = getFilePath(selectedFileUri);
                selectedFileName = getFileName(selectedFileUri);
                filePath.setText(selectedFilePath);
                fileName.setText(selectedFileName);
            }
        }
    }

    public String getImageFileName(Uri uri) {
        String filePath = getImagePath(uri);
        File file = new File(filePath);
        return file.getName();
    }

    public String getImagePath(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        int column_index = cursor
                .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    public String getFilePath(Uri uri) {
        String[] projection = {MediaStore.Files.FileColumns.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        if (path == null) {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                File tempFile = createTempFileFromStream(inputStream);
                return tempFile.getAbsolutePath();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return path;
        }
    }

    private File createTempFileFromStream(InputStream inputStream) throws IOException {
        File tempFile = File.createTempFile("temp", null, getCacheDir());
        FileOutputStream outputStream = new FileOutputStream(tempFile);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.close();
        inputStream.close();
        return tempFile;
    }

    public String getFileName(Uri uri) {
        String filePath = getFilePath(uri);
        if(filePath==null)
        {
            return "Problem";
        }
        File file = new File(filePath);
        return file.getName();
    }

    private class SenderRxThread extends Thread {
        private sendActivity activity;
        String dstAddress;
        int dstPort;

        SenderRxThread(String address, int port,sendActivity activity) {
            this.dstAddress = address;
            this.dstPort = port;
            this.activity = activity;
        }

        @Override
        public void run() {
            Socket socket = null;

            try {
                socket = new Socket(dstAddress, dstPort);
                Sender sender = new Sender(activity);
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity,"Please wait...",Toast.LENGTH_LONG).show();
                    }
                });
                sender.sendFile(socket,dis,dos,filePath.getText().toString(),fileName.getText().toString());

                /*File file = new File(filePath.getText().toString());

                int bufferSize = 1024;
                byte[] buffer = new byte[bufferSize];
                int bytesRead;
                BufferedInputStream bis;
                bis = new BufferedInputStream(new FileInputStream(file));
                OutputStream os = socket.getOutputStream();

                while ((bytesRead = bis.read(buffer, 0, bufferSize)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }


                sendActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(sendActivity.this,
                                "File Sent Successfully",
                                Toast.LENGTH_LONG).show();
                    }});*/


            } catch (IOException e) {

                e.printStackTrace();

                final String eMsg = "Something wrong: " + e.getMessage();
                sendActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(sendActivity.this,
                                eMsg,
                                Toast.LENGTH_LONG).show();
                    }});

            } finally {
                if(socket != null){
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}