package com.example.ktkq.mediaplayer;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;

import com.example.ktkq.vedio.media.IjkPlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private IjkPlayerView mPlayerView;
    private Toolbar mToolbar;

    Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        initView();
        init();
        initIndicator();
        initEvent();
        initViewpage();
        initData();
    }

    private void initView() {
        mPlayerView = findViewById(R.id.player_view);
        mToolbar = findViewById(R.id.toolbar);
    }

    private void init() {
        setSupportActionBar(mToolbar);
        ActionBar bar = getSupportActionBar();
        if (null != bar)
            bar.setDisplayHomeAsUpEnabled(true);
    }

    /**
     * 初始化适配器
     */
    private void initIndicator() {
    }

    private void initEvent() {
    }

    private boolean isFirstMediaInit = true;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initData();
    }

    /**
     * 初始化数据
     */
    private void initData() {
        String title = "这里我来组成头部";
        String url = "http://wxsnsdy.tc.qq.com/105/20210/snsdyvideodownload?filekey=30280201010421301f0201690402534804102ca905ce620b1241b726bc41dcff44e00204012882540400&bizid=1023&hy=SH&fileparam=302c020101042530230204136ffd93020457e3c4ff02024ef202031e8d7f02030f42400204045a320a0201000400";
        if (isFirstMediaInit) {
            int steptime = 0;
            mPlayerView.init()
                    .setTitle(title)
                    .setSkipTip(steptime)
                    .setVideoPath(url)
                    .start();
            isFirstMediaInit = false;
        } else {
            mPlayerView.setTitle(title);
            mPlayerView.setOtherUrlAndPlay(url);
        }
    }

    /**
     * 初始化切页
     */
    private void initViewpage() {

    }


    @Override
    protected void onResume() {
        super.onResume();
        mPlayerView.onResume();
        mPlayerView.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPlayerView.onPause();
    }

    @Override
    public void onBackPressed() {
        if (mPlayerView.onBackPressed()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mPlayerView.configurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPlayerView.onDestroy();
    }
}
