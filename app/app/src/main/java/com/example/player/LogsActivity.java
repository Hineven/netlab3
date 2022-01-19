package com.example.player;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class LogsActivity extends AppCompatActivity {

    private TextView logs_view;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);
        logs_view = ActivityCompat.requireViewById(this, R.id.logs_text);
        logs_view.setText(getIntent().getStringExtra("logs"));
    }
}
