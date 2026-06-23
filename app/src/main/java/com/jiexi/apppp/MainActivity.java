package com.jiexi.apppp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.jiexi.apppp.ui.BilibiliActivity;
import com.jiexi.apppp.ui.DouyinActivity;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnBilibili = (Button) findViewById(R.id.btnBilibili);
        btnBilibili.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, BilibiliActivity.class));
            }
        });

        Button btnDouyin = (Button) findViewById(R.id.btnDouyin);
        btnDouyin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DouyinActivity.class));
            }
        });
    }
}
