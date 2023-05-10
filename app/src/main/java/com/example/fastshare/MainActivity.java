package com.example.fastshare;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn_send = findViewById(R.id.btn_send);
        Button btn_receive = findViewById(R.id.btn_receive);

        btn_send.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this,sendActivity.class);
            startActivity(intent);
        });

        btn_receive.setOnClickListener(view->{
            Intent intent = new Intent(MainActivity.this,receiveActivity.class);
            startActivity(intent);
        });
    }
}