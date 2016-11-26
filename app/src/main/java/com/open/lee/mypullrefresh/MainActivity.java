package com.open.lee.mypullrefresh;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.open.lee.mypullrefresh.Listener.OnRefreshListener;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final MyRefreshTextView refreshTextView = new MyRefreshTextView(this);
        setContentView(R.layout.activity_main);
        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.id_layout_main);
        refreshTextView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        refreshTextView.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshTextView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshTextView.completeRefresh();
                    }
                }, 1500);
            }
        });
        linearLayout.addView(refreshTextView);
    }
}
