package com.example.ktkq.vedio.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.IntDef;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorListenerAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


import com.example.ktkq.mediaplayer.R;
import com.example.ktkq.vedio.utils.AnimHelper;
import com.example.ktkq.vedio.utils.MotionEventUtils;
import com.example.ktkq.vedio.utils.SoftInputUtils;
import com.example.ktkq.vedio.utils.SystemBrightManager;
import com.example.ktkq.vedio.utils.WindowUtils;
import com.example.ktkq.vedio.widgets.MarqueeTextView;

import java.io.FileDescriptor;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static android.view.GestureDetector.OnGestureListener;
import static android.view.GestureDetector.SimpleOnGestureListener;
import static android.widget.SeekBar.OnSeekBarChangeListener;
import static com.example.ktkq.vedio.utils.StringUtils.generateTime;
import static tv.danmaku.ijk.media.player.IMediaPlayer.OnInfoListener;

public class IjkPlayerView extends FrameLayout implements View.OnClickListener {

    // 进度条最大值
    private static final int MAX_VIDEO_SEEK = 1000;
    // 默认隐藏控制栏时间
    private static final int DEFAULT_HIDE_TIMEOUT = 5000;
    // 更新进度消息
    private static final int MSG_UPDATE_SEEK = 10086;
    // 使能翻转消息
    private static final int MSG_ENABLE_ORIENTATION = 10087;
    // 无效变量
    private static final int INVALID_VALUE = -1;

    // 原生的IjkPlayer
    private IjkVideoView mVideoView;
    // 加载
    private ProgressBar mLoadingView;
    // 音量
    private TextView mTvVolume;
    // 亮度
    private TextView mTvBrightness;
    // 快进
    private TextView mTvFastForward;
    // 触摸信息布局
    private FrameLayout mFlTouchLayout;

    // 顶部栏
    private LinearLayout mLlTopbar;
    // 顶部栏右边
    private LinearLayout mLlTopbarExtend;
    // 顶部栏后退键
    private RelativeLayout mRlBack;
    // 顶部栏标题
    private MarqueeTextView mTvTitle;

    // 全屏播放按钮
    private TextView mTvPlayBig;

    // 播放键
    private RelativeLayout mRlPlay;
    // 播放键按钮
    private TextView mTvPlay;
    // 当前时间
    private TextView mTvTimeLeft;
    // 结束时间
    private TextView mTvTimeRight;
    // 进度条
    private SeekBar mPlayerSeek;
    //扩展
    private RelativeLayout mRlExpand;

    // 底部栏
    private RelativeLayout mRlBottom;
    // 整个视频框架布局
    private FrameLayout mFlVideoBox;
    // 锁屏键
    private ImageView mIvPlayerLock;
    // 还原屏幕
    private TextView mTvRecoverScreen;
    // 关联的Activity
    private AppCompatActivity mAttachActivity;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_UPDATE_SEEK) {
                final int pos = _setProgress();
                if (!mIsSeeking && mIsShowBar && mVideoView.isPlaying()) {
                    // 这里会重复发送MSG，已达到实时更新 Seek 的效果
                    msg = obtainMessage(MSG_UPDATE_SEEK);
                    sendMessageDelayed(msg, 1000 - (pos % 1000));
                }
            } else if (msg.what == MSG_ENABLE_ORIENTATION) {
                if (mOrientationListener != null) {
                    mOrientationListener.enable();
                }
            }
        }
    };
    // 音量控制
    private AudioManager mAudioManager;
    // 手势控制
    private GestureDetector mGestureDetector;
    // 最大音量
    private int mMaxVolume;
    // 锁屏
    private boolean mIsForbidTouch = false;
    // 是否显示控制栏
    private boolean mIsShowBar = true;
    // 是否全屏
    private boolean mIsFullscreen;
    // 是否播放结束
    private boolean mIsPlayComplete = false;
    // 是否正在拖拽进度条
    private boolean mIsSeeking;
    // 目标进度
    private long mTargetPosition = INVALID_VALUE;
    // 当前进度
    private int mCurPosition = INVALID_VALUE;
    // 当前音量
    private int mCurVolume = INVALID_VALUE;
    // 当前亮度
    private float mCurBrightness = INVALID_VALUE;
    // 初始高度
    private int mInitHeight;
    // 屏幕宽/高度
    private int mWidthPixels;
    // 屏幕UI可见性
    private int mScreenUiVisibility;
    // 屏幕旋转角度监听
    private OrientationEventListener mOrientationListener;
    // 进来还未播放
    private boolean mIsNeverPlay = true;
    // 外部监听器
    private OnInfoListener mOutsideInfoListener;
    // 禁止翻转，默认为禁止
    private boolean mIsForbidOrientation = true;
    // 是否固定全屏状态
    private boolean mIsAlwaysFullScreen = false;
    // 记录按退出全屏时间
    private long mExitTime = 0;
    // 视频Matrix
    private Matrix mVideoMatrix = new Matrix();
    private Matrix mSaveMatrix = new Matrix();
    // 是否需要显示恢复屏幕按钮
    private boolean mIsNeedRecoverScreen = false;

    //字幕
    private RelativeLayout mRLZimu;
    //中英切换
    private RelativeLayout mRlCE;
    //中英切换图片
    private TextView mTvCE;
    //英文字幕 或者上方字幕
    private TextView mTvEnglish;
    //中文字幕 或者下方字幕
    private TextView mTvChinese;
    //当前字幕状态
    private int CEStatus;

    public IjkPlayerView(Context context) {
        this(context, null);
    }

    public IjkPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        _initView(context);
    }

    private void _initView(Context context) {
        if (context instanceof AppCompatActivity) {
            mAttachActivity = (AppCompatActivity) context;
        } else {
            throw new IllegalArgumentException("Context must be AppCompatActivity");
        }
        View.inflate(context, R.layout.vedio_layout_player_view, this);
        mVideoView = findViewById(R.id.video_view);
        mLoadingView = findViewById(R.id.pb_loading);
        mTvVolume = findViewById(R.id.tv_volume);
        mTvBrightness = findViewById(R.id.tv_brightness);
        mTvFastForward = findViewById(R.id.tv_fast_forward);
        mFlTouchLayout = findViewById(R.id.fl_touch_layout);

        mLlTopbar = findViewById(R.id.ll_topbar);
        mLlTopbarExtend = findViewById(R.id.ll_topbar_extend);
        mRlBack = findViewById(R.id.rl_back);
        mTvTitle = findViewById(R.id.tv_title);

        mRlPlay = findViewById(R.id.rl_play);
        mTvPlay = findViewById(R.id.tv_play);
        mTvPlayBig = findViewById(R.id.tv_play_full);

        mTvTimeLeft = findViewById(R.id.tv_time_left);
        mPlayerSeek = findViewById(R.id.player_seek);
        mTvTimeRight = findViewById(R.id.tv_time_right);
        mRlExpand = findViewById(R.id.rl_expand);
        mRLZimu = findViewById(R.id.rl_zimu);

        mRlBottom = findViewById(R.id.rl_bottom);
        mFlVideoBox = findViewById(R.id.fl_video_box);
        mIvPlayerLock = findViewById(R.id.iv_player_lock);
        mTvRecoverScreen = findViewById(R.id.tv_recover_screen);

        mRlCE = findViewById(R.id.rl_ce);
        mTvCE = findViewById(R.id.tv_ce);
        mTvEnglish = findViewById(R.id.tv_english);
        mTvChinese = findViewById(R.id.tv_chinese);
        mRlPlay = findViewById(R.id.rl_play);
        _initVideoSkip();
        _initReceiver();
        initEvent();
        showContorlBar(true);
    }

    private void initEvent() {
        mRlPlay.setOnClickListener(this);
        mTvPlayBig.setOnClickListener(this);
        mRlBack.setOnClickListener(this);
        mRlExpand.setOnClickListener(this);
        mIvPlayerLock.setOnClickListener(this);
        mRlPlay.setOnClickListener(this);
        mTvRecoverScreen.setOnClickListener(this);
        mRlCE.setOnClickListener(this);
    }

    /**
     * 初始化
     */
    private void _initMediaPlayer() {
        // 加载 IjkMediaPlayer 库
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");
        // 声音
        mAudioManager = (AudioManager) mAttachActivity.getSystemService(Context.AUDIO_SERVICE);
        mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        // 初始化亮度
        if (!SystemBrightManager.isAutoBrightness(mAttachActivity)) {
            try {
                int e = Settings.System.getInt(mAttachActivity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                float progress = 1.0F * (float) e / 255.0F;
                WindowManager.LayoutParams layout = mAttachActivity.getWindow().getAttributes();
                layout.screenBrightness = progress;
                if (progress > 0 && progress < 1) {
                    mAttachActivity.getWindow().setAttributes(layout);
                }
            } catch (Settings.SettingNotFoundException var7) {
                var7.printStackTrace();
            }
        }
        // 进度
        mPlayerSeek.setMax(MAX_VIDEO_SEEK);
        mPlayerSeek.setOnSeekBarChangeListener(mSeekListener);
        // 视频监听
        mVideoView.setOnInfoListener(mInfoListener);
        // 触摸控制
        mGestureDetector = new GestureDetector(mAttachActivity, mPlayerGestureListener);
        mFlVideoBox.setClickable(true);
        mFlVideoBox.setOnTouchListener(mPlayerTouchListener);
        // 屏幕翻转控制
        mOrientationListener = new OrientationEventListener(mAttachActivity) {
            @Override
            public void onOrientationChanged(int orientation) {
                _handleOrientation(orientation);
            }
        };
        if (mIsForbidOrientation) {
            // 禁止翻转
            mOrientationListener.disable();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mInitHeight == 0) {
            mInitHeight = getHeight();
            mWidthPixels = getResources().getDisplayMetrics().widthPixels;
        }
    }

    /**============================ 外部调用接口 ============================*/

    /**
     * Activity.onResume() 里调用
     */
    public void onResume() {
        Log.i("TTAG", "onResume");
        if (mIsScreenLocked) {
            // 如果出现锁屏则需要重新渲染器Render，不然会出现只有声音没有动画
            // 目前只在锁屏时会出现图像不动的情况，如果有遇到类似情况可以尝试按这个方法解决
            mIsScreenLocked = false;
        }
        mVideoView.setRender(IjkVideoView.RENDER_TEXTURE_VIEW);
        mVideoView.resume();
        if (!mIsForbidTouch && !mIsForbidOrientation) {
            mOrientationListener.enable();
        }
        if (mCurPosition != INVALID_VALUE) {
            // 重进后 seekTo 到指定位置播放时，通常会回退到前几秒，关键帧??
            seekTo(mCurPosition);
            mCurPosition = INVALID_VALUE;
        }
    }

    /**
     * Activity.onPause() 里调用
     */
    public void onPause() {
        Log.i("TTAG", "onPause");
        mCurPosition = mVideoView.getCurrentPosition();
        mVideoView.pause();
        mOrientationListener.disable();
        _pauseDanmaku();
        setPlayStatusStop();
    }

    /**
     * 显示正在播放的按钮
     */
    private void setPlayStatusPlay() {
        mTvPlay.setBackgroundResource(R.drawable.video_btn_stop);
        mTvPlayBig.setBackgroundResource(R.drawable.vedio_full_btn_stop);
    }

    /**
     * 显示停止播放的按钮
     */
    private void setPlayStatusStop() {
        mTvPlay.setBackgroundResource(R.drawable.video_btn_play);
        mTvPlayBig.setBackgroundResource(R.drawable.vedio_full_btn_play);
    }

    /**
     * Activity.onDestroy() 里调用
     *
     * @return 返回播放进度
     */
    public int onDestroy() {
        // 记录播放进度
        int curPosition = mVideoView.getCurrentPosition();
        new Thread(new Runnable() {
            @Override
            public void run() {
                mVideoView.destroy();
            }
        }).start();

        IjkMediaPlayer.native_profileEnd();
        if (mDanmakuView != null) {
            mDanmakuView.release();
            mDanmakuView = null;
        }
        // 注销广播
        mAttachActivity.unregisterReceiver(mScreenReceiver);
        // 关闭屏幕常亮
        mAttachActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        return curPosition;
    }

    /**
     * 处理音量键，避免外部按音量键后导航栏和状态栏显示出来退不回去的状态
     *
     * @param keyCode
     * @return
     */
    public boolean handleVolumeKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            _setVolume(true);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            _setVolume(false);
            return true;
        } else {
            return false;
        }
    }

    /**
     * 回退，全屏时退回竖屏
     *
     * @return
     */
    public boolean onBackPressed() {
        if (recoverFromEditVideo()) {
            return true;
        }
        if (mIsAlwaysFullScreen) {
            _exit();
            return true;
        } else if (mIsFullscreen) {
            mRLZimu.setVisibility(View.GONE);
            mAttachActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            if (mIsForbidTouch) {
                // 锁住状态则解锁
                mIsForbidTouch = false;
                mIvPlayerLock.setSelected(false);
                _setControlBarVisible(mIsShowBar);
            }
            return true;
        }
        return false;
    }

    /**
     * 初始化，必须要先调用
     *
     * @return
     */
    public IjkPlayerView init() {
        _initMediaPlayer();
        _setControlBarVisible(true);
        return this;
    }

    /**
     * 设置播放资源
     *
     * @param url
     * @return
     */
    public IjkPlayerView setVideoPath(String url) {
        return setVideoPath(Uri.parse(url));
    }

    /**
     * 设置播放资源
     *
     * @param uri
     * @return
     */
    public IjkPlayerView setVideoPath(Uri uri) {
        mVideoView.setVideoURI(uri);
        if (mCurPosition != INVALID_VALUE) {
            seekTo(mCurPosition);
            mCurPosition = INVALID_VALUE;
        } else {
            seekTo(0);
        }
        return this;
    }

    /**
     * 设置播放资源
     */
    public IjkPlayerView setVideoPath(FileDescriptor fileDescriptor) {
        mVideoView.setVideoURI(fileDescriptor);
        if (mCurPosition != INVALID_VALUE) {
            seekTo(mCurPosition);
            mCurPosition = INVALID_VALUE;
        } else {
            seekTo(0);
        }
        return this;
    }

    /**
     * 设置标题，全屏的时候可见
     */
    public IjkPlayerView setTitle(String title) {
        mTvTitle.setText(title);
        return this;
    }

    /**
     * 设置只显示全屏状态
     */
    public IjkPlayerView alwaysFullScreen() {
        mIsAlwaysFullScreen = true;
        _setFullScreen(true);
        mAttachActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        _setUiLayoutFullscreen();
        return this;
    }

    /**
     * 开始播放
     */
    public void start() {
        if (mIsPlayComplete) {
            if (mDanmakuView != null && mDanmakuView.isPrepared()) {
                mDanmakuView.seekTo((long) 0);
                mDanmakuView.pause();
            }
            mIsPlayComplete = false;
        }
        if (!mVideoView.isPlaying()) {
            setPlayStatusPlay();
            mVideoView.start();
            // 更新进度
            mHandler.sendEmptyMessage(MSG_UPDATE_SEEK);
        }
        if (mIsNeverPlay) {
            mIsNeverPlay = false;
            setPlayStatusStop();
            mLoadingView.setVisibility(VISIBLE);
//            mIsShowBar = false;
            // 放这边装载弹幕，不然会莫名其妙出现多切几次到首页会弹幕自动播放问题，这里处理下
            _loadDanmaku();
        }
        // 视频播放时开启屏幕常亮
        mAttachActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * 暂停
     */
    public void pause() {
        setPlayStatusStop();
        if (mVideoView.isPlaying()) {
            mVideoView.pause();
        }
        _pauseDanmaku();
        // 视频暂停时关闭屏幕常亮
        mAttachActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * 跳转
     *
     * @param position 位置
     */
    public void seekTo(int position) {
        mVideoView.seekTo(position);
        mDanmakuTargetPosition = position;
    }

    /**
     * 停止
     */
    public void stop() {
        pause();
        mVideoView.stopPlayback();
    }

    public void reset() {

    }

    /**============================ 控制栏处理 ============================*/

    /**
     * SeekBar监听
     */
    private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {

        private long curPosition;

        @Override
        public void onStartTrackingTouch(SeekBar bar) {
            mIsSeeking = true;
            _showControlBar(3600000);
            mHandler.removeMessages(MSG_UPDATE_SEEK);
            curPosition = mVideoView.getCurrentPosition();
        }

        @Override
        public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            if (!fromUser) {
                return;
            }
            long duration = mVideoView.getDuration();
            // 计算目标位置
            mTargetPosition = (duration * progress) / MAX_VIDEO_SEEK;
            int deltaTime = (int) ((mTargetPosition - curPosition) / 1000);
            String desc;
            // 对比当前位置来显示快进或后退
            if (mTargetPosition > curPosition) {
                desc = generateTime(mTargetPosition) + "/" + generateTime(duration) + "\n" + "+" + deltaTime + "秒";
            } else {
                desc = generateTime(mTargetPosition) + "/" + generateTime(duration) + "\n" + deltaTime + "秒";
            }
            _setFastForward(desc);
        }

        @Override
        public void onStopTrackingTouch(SeekBar bar) {
            _hideTouchView();
            mIsSeeking = false;
            // 视频跳转
            seekTo((int) mTargetPosition);
            mTargetPosition = INVALID_VALUE;
            _setProgress();
            _showControlBar(DEFAULT_HIDE_TIMEOUT);
        }
    };

    /**
     * 隐藏视图Runnable
     */
    private Runnable mHideBarRunnable = new Runnable() {
        @Override
        public void run() {
            _hideAllView(false);
        }
    };

    /**
     * 隐藏除视频外所有视图
     */
    private void _hideAllView(boolean isTouchLock) {
        mFlTouchLayout.setVisibility(View.GONE);
        hideTopbar();
        mRlBottom.setVisibility(View.GONE);
        mTvPlayBig.setVisibility(View.GONE);
        if (!isTouchLock) {
            mIvPlayerLock.setVisibility(View.GONE);
            mIsShowBar = false;
        }
        if (mIsEnableDanmaku) {
            mDanmakuPlayerSeek.setVisibility(GONE);
        }
        if (mIsNeedRecoverScreen) {
            mTvRecoverScreen.setVisibility(GONE);
        }
    }

    /**
     * 设置控制栏显示或隐藏
     *
     * @param isShowBar
     */
    private void _setControlBarVisible(boolean isShowBar) {
        if (mIsNeverPlay) {
            mTvPlay.setVisibility(isShowBar ? View.VISIBLE : View.GONE);
        } else if (mIsForbidTouch) {
            mIvPlayerLock.setVisibility(isShowBar ? View.VISIBLE : View.GONE);
        } else {
            if (isShowBar) {
                showTopbar();
            } else {
                hideTopbar();
            }
            mRlBottom.setVisibility(isShowBar ? View.VISIBLE : View.GONE);
            // 全屏切换显示的控制栏不一样
            if (mIsFullscreen) {
                setTopbarTypeFull();
                setBotbarTypeFull();
                showPlayFull();
                mTvPlayBig.setVisibility(isShowBar ? View.VISIBLE : View.GONE);
                mIvPlayerLock.setVisibility(isShowBar ? View.VISIBLE : View.GONE);
                if (mIsEnableDanmaku) {
                    mDanmakuPlayerSeek.setVisibility(isShowBar ? View.VISIBLE : View.GONE);
                }
                if (mIsNeedRecoverScreen) {
                    mTvRecoverScreen.setVisibility(isShowBar ? View.VISIBLE : View.GONE);
                }
            } else {
                setTopbarTypeSmall();
                setBotbarTypeSmall();
                hidePlayFull();
                mTvPlayBig.setVisibility(View.GONE);
                mIvPlayerLock.setVisibility(View.GONE);
                if (mIsEnableDanmaku) {
                    mDanmakuPlayerSeek.setVisibility(GONE);
                }
                if (mIsNeedRecoverScreen) {
                    mTvRecoverScreen.setVisibility(View.GONE);
                }
            }
        }
    }

    /**
     * 显示全屏播放按钮
     */
    private void showPlayFull() {
        mTvPlayBig.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏全屏播放按钮
     */
    private void hidePlayFull() {
        mTvPlayBig.setVisibility(View.GONE);
    }

    /**
     * 显示顶部栏
     */
    private void showTopbar() {
        mLlTopbar.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏顶部栏
     */
    private void hideTopbar() {
        mLlTopbar.setVisibility(View.GONE);
    }

    /**
     * 设置小屏的顶部栏
     */
    private void setTopbarTypeSmall() {
        mLlTopbarExtend.setVisibility(View.VISIBLE);
        mLlTopbar.setBackgroundResource(R.drawable.vedio_pic_mask_up);
    }

    /**
     * 设置全屏的底部栏
     */
    private void setTopbarTypeFull() {
        mLlTopbarExtend.setVisibility(View.GONE);
        mLlTopbar.setBackgroundResource(R.drawable.vedio_full_pic_mask_up);
    }

    /**
     * 设置小屏的底部栏
     */
    private void setBotbarTypeSmall() {
        mRlExpand.setVisibility(isStudy ? View.VISIBLE : View.GONE);
        mRlBottom.setBackgroundResource(R.drawable.vedio_pic_mask);
    }

    /**
     * 设置全屏的顶部栏
     */
    private void setBotbarTypeFull() {
        mRlExpand.setVisibility(View.GONE);
        mRlBottom.setBackgroundResource(R.drawable.vedio_full_pic_mask);
    }

    /**
     * 开关控制栏，单击界面的时候
     */
    private void _toggleControlBar() {
        if (isHideAll) {
            return;
        }
        mIsShowBar = !mIsShowBar;
        _setControlBarVisible(mIsShowBar);
        if (mIsShowBar) {
            // 发送延迟隐藏控制栏的操作
            mHandler.postDelayed(mHideBarRunnable, DEFAULT_HIDE_TIMEOUT);
            // 发送更新 Seek 消息
            mHandler.sendEmptyMessage(MSG_UPDATE_SEEK);
        }
    }

    /**
     * 显示控制栏
     *
     * @param timeout 延迟隐藏时间
     */
    private void _showControlBar(int timeout) {
        if (!mIsShowBar) {
            _setProgress();
            mIsShowBar = true;
        }
        _setControlBarVisible(true);
        mHandler.sendEmptyMessage(MSG_UPDATE_SEEK);
        // 先移除隐藏控制栏 Runnable，如果 timeout=0 则不做延迟隐藏操作
        mHandler.removeCallbacks(mHideBarRunnable);
        if (timeout != 0) {
            mHandler.postDelayed(mHideBarRunnable, timeout);
        }
    }

    public void playOrPause() {
        _togglePlayStatus();
    }

    /**
     * 获取开始或暂停按钮状态
     */
    public boolean getPlayOrPauseStatus() {
        return !mVideoView.isPlaying();
    }

    /**
     * 切换播放状态，点击播放按钮时
     */
    private void _togglePlayStatus() {
        if (mVideoView.isPlaying()) {
            pause();
        } else {
            start();
        }
    }

    public boolean isPlaying() {
        return mVideoView.isPlaying();
    }

    /**
     * 刷新隐藏控制栏的操作
     */
    private void _refreshHideRunnable() {
        mHandler.removeCallbacks(mHideBarRunnable);
        mHandler.postDelayed(mHideBarRunnable, DEFAULT_HIDE_TIMEOUT);
    }


    /**
     * 刷新隐藏控制栏的操作
     */
    public void refreshHideRunnable() {
        mHandler.removeCallbacks(mHideBarRunnable);
        mHandler.postDelayed(mHideBarRunnable, 10000);
    }

    /**
     * 切换控制锁
     */
    private void _togglePlayerLock() {
        mIsForbidTouch = !mIsForbidTouch;
        mIvPlayerLock.setSelected(mIsForbidTouch);
        if (mIsForbidTouch) {
            mOrientationListener.disable();
            _hideAllView(true);
        } else {
            if (!mIsForbidOrientation) {
                mOrientationListener.enable();
            }
            mLlTopbar.setVisibility(View.VISIBLE);
            mRlBottom.setVisibility(View.VISIBLE);
            if (mIsEnableDanmaku) {
                mDanmakuPlayerSeek.setVisibility(VISIBLE);
            }
            if (mIsNeedRecoverScreen) {
                mTvRecoverScreen.setVisibility(VISIBLE);
            }
        }
    }

    @Override
    public void onClick(View v) {
        _refreshHideRunnable();
        switch (v.getId()) {
            // 点击返回
            case R.id.rl_back:
                if (mIsAlwaysFullScreen) {
                    _exit();
                    return;
                }
                if (mIsFullscreen) {
                    mRLZimu.setVisibility(View.GONE);
                    mAttachActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } else {
                    mAttachActivity.finish();
                }
                break;
            // 播放
            case R.id.rl_play:
            case R.id.tv_play_full:
                _togglePlayStatus();
                break;
            // 切换全屏
            case R.id.rl_expand:
                _toggleFullScreen();
                break;
            // 锁定
            case R.id.iv_player_lock:
                _togglePlayerLock();
                break;
            // 快捷跳转
            case R.id.tv_do_skip:
                mLoadingView.setVisibility(VISIBLE);
                seekTo(mSkipPosition);
                mHandler.removeCallbacks(mHideSkipTipRunnable);
                _hideSkipTip();
                _setProgress();
                break;
            // 快捷跳转(取消)
            case R.id.iv_cancel_skip:
                mHandler.removeCallbacks(mHideSkipTipRunnable);
                _hideSkipTip();
                break;
            // 还原屏幕
            case R.id.tv_recover_screen:
                mVideoView.resetVideoView(true);
                mIsNeedRecoverScreen = false;
                mTvRecoverScreen.setVisibility(GONE);
                break;
            // 中英切换
            case R.id.rl_ce:
                break;
        }
    }

    /**==================== 屏幕翻转/切换处理 ====================*/

    /**
     * 使能视频翻转
     */
    public IjkPlayerView enableOrientation() {
        mIsForbidOrientation = false;
        mOrientationListener.enable();
        return this;
    }

    /**
     * 全屏切换，点击全屏按钮
     */
    private void _toggleFullScreen() {
        if (WindowUtils.getScreenOrientation(mAttachActivity) == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            //现在是竖屏
            mRLZimu.setVisibility(View.GONE);
            mAttachActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            //现在是横屏
            mRLZimu.setVisibility(View.VISIBLE);
            mAttachActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    /**
     * 设置全屏或窗口模式
     *
     * @param isFullscreen
     */
    private void _setFullScreen(boolean isFullscreen) {
        mIsFullscreen = isFullscreen;
        // 处理弹幕相关视图
        _toggleDanmakuView(isFullscreen);
        _handleActionBar(isFullscreen);
        _changeHeight(isFullscreen);
        mHandler.post(mHideBarRunnable);
        // 处理三指旋转缩放，如果之前进行了相关操作则全屏时还原之前旋转缩放的状态，窗口模式则将整个屏幕还原为未操作状态
        if (mIsNeedRecoverScreen) {
            if (isFullscreen) {
                mVideoView.adjustVideoView(1.0f);
                mTvRecoverScreen.setVisibility(mIsShowBar ? View.VISIBLE : View.GONE);
            } else {
                mVideoView.resetVideoView(false);
                mTvRecoverScreen.setVisibility(GONE);
            }
        }
    }

    /**
     * 处理屏幕翻转
     *
     * @param orientation
     */
    private void _handleOrientation(int orientation) {
        if (mIsNeverPlay) {
            return;
        }
        if (mIsFullscreen && !mIsAlwaysFullScreen) {
            // 根据角度进行竖屏切换，如果为固定全屏则只能横屏切换
            if (orientation >= 0 && orientation <= 30 || orientation >= 330) {
                // 请求屏幕翻转
                mAttachActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        } else {
            // 根据角度进行横屏切换
            if (orientation >= 60 && orientation <= 120) {
                mAttachActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            } else if (orientation >= 240 && orientation <= 300) {
                mAttachActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }
    }

    /**
     * 当屏幕执行翻转操作后调用禁止翻转功能，延迟3000ms再使能翻转，避免不必要的翻转
     */
    private void _refreshOrientationEnable() {
        if (!mIsForbidOrientation) {
            mOrientationListener.disable();
            mHandler.removeMessages(MSG_ENABLE_ORIENTATION);
            mHandler.sendEmptyMessageDelayed(MSG_ENABLE_ORIENTATION, 3000);
        }
    }

    /**
     * 隐藏/显示 ActionBar
     *
     * @param isFullscreen
     */
    private void _handleActionBar(boolean isFullscreen) {
        ActionBar supportActionBar = mAttachActivity.getSupportActionBar();
        if (supportActionBar != null) {
            if (isFullscreen) {
                supportActionBar.hide();
            } else {
                supportActionBar.show();
            }
        }
    }

    /**
     * 改变视频布局高度
     *
     * @param isFullscreen
     */
    private void _changeHeight(boolean isFullscreen) {
        if (mIsAlwaysFullScreen) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (isFullscreen) {
            // 高度扩展为横向全屏
            layoutParams.height = mWidthPixels;
        } else {
            // 还原高度
            layoutParams.height = mInitHeight;
        }
        setLayoutParams(layoutParams);
    }

    /**
     * 设置UI沉浸式显示
     */
    private void _setUiLayoutFullscreen() {
        if (Build.VERSION.SDK_INT >= 14) {
            // 获取关联 Activity 的 DecorView
            View decorView = mAttachActivity.getWindow().getDecorView();
            // 沉浸式使用这些Flag
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            mAttachActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    /**
     * 屏幕翻转后的处理，在 Activity.configurationChanged() 调用
     * SYSTEM_UI_FLAG_LAYOUT_STABLE：维持一个稳定的布局
     * SYSTEM_UI_FLAG_FULLSCREEN：Activity全屏显示，且状态栏被隐藏覆盖掉
     * SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN：Activity全屏显示，但状态栏不会被隐藏覆盖，状态栏依然可见，Activity顶端布局部分会被状态遮住
     * SYSTEM_UI_FLAG_HIDE_NAVIGATION：隐藏虚拟按键(导航栏)
     * SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION：效果同View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
     * SYSTEM_UI_FLAG_IMMERSIVE：沉浸式，从顶部下滑出现状态栏和导航栏会固定住
     * SYSTEM_UI_FLAG_IMMERSIVE_STICKY：黏性沉浸式，从顶部下滑出现状态栏和导航栏过几秒后会缩回去
     *
     * @param newConfig
     */
    public void configurationChanged(Configuration newConfig) {
        _refreshOrientationEnable();
        // 沉浸式只能在SDK19以上实现
        if (Build.VERSION.SDK_INT >= 14) {
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // 获取关联 Activity 的 DecorView
                View decorView = mAttachActivity.getWindow().getDecorView();
                // 保存旧的配置
                mScreenUiVisibility = decorView.getSystemUiVisibility();
                // 沉浸式使用这些Flag
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
                _setFullScreen(true);
                mAttachActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                View decorView = mAttachActivity.getWindow().getDecorView();
                // 还原
                decorView.setSystemUiVisibility(mScreenUiVisibility);
                _setFullScreen(false);
                mAttachActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }
    }

    /**
     * 从总显示全屏状态退出处理{@link #alwaysFullScreen()}
     */
    private void _exit() {
        if (System.currentTimeMillis() - mExitTime > 2000) {
            Toast.makeText(mAttachActivity, "再按一次退出", Toast.LENGTH_SHORT).show();
            mExitTime = System.currentTimeMillis();
        } else {
            mAttachActivity.finish();
        }
    }

    /**============================ 触屏操作处理 ============================*/

    /**
     * 手势监听
     */
    private OnGestureListener mPlayerGestureListener = new SimpleOnGestureListener() {
        // 是否是按下的标识，默认为其他动作，true为按下标识，false为其他动作
        private boolean isDownTouch;
        // 是否声音控制,默认为亮度控制，true为声音控制，false为亮度控制
        private boolean isVolume;
        // 是否横向滑动，默认为纵向滑动，true为横向滑动，false为纵向滑动
        private boolean isLandscape;
        // 是否从弹幕编辑状态返回
        private boolean isRecoverFromDanmaku;

        @Override
        public boolean onDown(MotionEvent e) {
            isDownTouch = true;
            isRecoverFromDanmaku = recoverFromEditVideo();
            return super.onDown(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!mIsForbidTouch && !mIsNeverPlay) {
                float mOldX = e1.getX(), mOldY = e1.getY();
                float deltaY = mOldY - e2.getY();
                float deltaX = mOldX - e2.getX();
                if (isDownTouch) {
                    // 判断左右或上下滑动
                    isLandscape = Math.abs(distanceX) >= Math.abs(distanceY);
                    // 判断是声音或亮度控制
                    isVolume = mOldX > getResources().getDisplayMetrics().widthPixels * 0.5f;
                    isDownTouch = false;
                }

                if (isLandscape) {
                    _onProgressSlide(-deltaX / mVideoView.getWidth());
                } else {
                    float percent = deltaY / mVideoView.getHeight();
                    if (isVolume) {
                        _onVolumeSlide(percent);
                    } else {
                        _onBrightnessSlide(percent);
                    }
                }
            }
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            // 弹幕编辑状态返回则不执行单击操作
            if (isRecoverFromDanmaku) {
                return true;
            }
            _toggleControlBar();
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // 如果未进行播放或从弹幕编辑状态返回则不执行双击操作
            if (mIsNeverPlay || isRecoverFromDanmaku) {
                return true;
            }
            if (!mIsForbidTouch) {
                _refreshHideRunnable();
                _togglePlayStatus();
            }
            return true;
        }
    };

    /**
     * 隐藏视图Runnable
     */
    private Runnable mHideTouchViewRunnable = new Runnable() {
        @Override
        public void run() {
            _hideTouchView();
        }
    };

    /**
     * 触摸监听
     */
    private OnTouchListener mPlayerTouchListener = new OnTouchListener() {
        // 触摸模式：正常、无效、缩放旋转
        private static final int NORMAL = 1;
        private static final int INVALID_POINTER = 2;
        private static final int ZOOM_AND_ROTATE = 3;
        // 触摸模式
        private int mode = NORMAL;
        // 缩放的中点
        private PointF midPoint = new PointF(0, 0);
        // 旋转角度
        private float degree = 0;
        // 用来标识哪两个手指靠得最近，我的做法是取最近的两指中点和余下一指来控制旋转缩放
        private int fingerFlag = INVALID_VALUE;
        // 初始间距
        private float oldDist;
        // 缩放比例
        private float scale;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (MotionEventCompat.getActionMasked(event)) {
                case MotionEvent.ACTION_DOWN:
                    mode = NORMAL;
                    mHandler.removeCallbacks(mHideBarRunnable);
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 3 && mIsFullscreen) {
                        _hideTouchView();
                        // 进入三指旋转缩放模式，进行相关初始化
                        mode = ZOOM_AND_ROTATE;
                        MotionEventUtils.midPoint(midPoint, event);
                        fingerFlag = MotionEventUtils.calcFingerFlag(event);
                        degree = MotionEventUtils.rotation(event, fingerFlag);
                        oldDist = MotionEventUtils.calcSpacing(event, fingerFlag);
                        // 获取视频的 Matrix
                        mSaveMatrix = mVideoView.getVideoTransform();
                    } else {
                        mode = INVALID_POINTER;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mode == ZOOM_AND_ROTATE) {
                        // 处理旋转
                        float newRotate = MotionEventUtils.rotation(event, fingerFlag);
                        mVideoView.setVideoRotation((int) (newRotate - degree));
                        // 处理缩放
                        mVideoMatrix.set(mSaveMatrix);
                        float newDist = MotionEventUtils.calcSpacing(event, fingerFlag);
                        scale = newDist / oldDist;
                        mVideoMatrix.postScale(scale, scale, midPoint.x, midPoint.y);
                        mVideoView.setVideoTransform(mVideoMatrix);
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    if (mode == ZOOM_AND_ROTATE) {
                        // 调整视频界面，让界面居中显示在屏幕
                        mIsNeedRecoverScreen = mVideoView.adjustVideoView(scale);
                        if (mIsNeedRecoverScreen && mIsShowBar) {
                            mTvRecoverScreen.setVisibility(VISIBLE);
                        }
                    }
                    mode = INVALID_POINTER;
                    break;
            }
            // 触屏手势处理
            if (mode == NORMAL) {
                if (mGestureDetector.onTouchEvent(event)) {
                    return true;
                }
                if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_UP) {
                    _endGesture();
                }
            }
            return false;
        }
    };

    /**
     * 更新进度条
     */
    private int _setProgress() {
        if (mVideoView == null || mIsSeeking) {
            return 0;
        }
        // 视频播放的当前进度
        int position = mVideoView.getCurrentPosition();
        // 视频总的时长
        int duration = mVideoView.getDuration();
        if (duration > 0) {
            // 转换为 Seek 显示的进度值
            long pos = (long) MAX_VIDEO_SEEK * position / duration;
            mPlayerSeek.setProgress((int) pos);
            mPlayerSeek.setSecondaryProgress(getBufferPosition() * 10);
            if (mIsEnableDanmaku) {
                mDanmakuPlayerSeek.setProgress((int) pos);
            }
        }
        // 获取缓冲的进度百分比，并显示在 Seek 的次进度
        int percent = mVideoView.getBufferPercentage();
        mPlayerSeek.setSecondaryProgress(percent * 10);
        if (mIsEnableDanmaku) {
            mDanmakuPlayerSeek.setSecondaryProgress(percent * 10);
        }
        // 更新播放时间
        mTvTimeRight.setText(generateTime(duration));
        mTvTimeLeft.setText(generateTime(position));
        // 返回当前播放进度
        return position;
    }

    /**
     * 设置快进
     */
    private void _setFastForward(String time) {
        if (mFlTouchLayout.getVisibility() == View.GONE) {
            mFlTouchLayout.setVisibility(View.VISIBLE);
        }
        if (mTvFastForward.getVisibility() == View.GONE) {
            mTvFastForward.setVisibility(View.VISIBLE);
        }
        mTvFastForward.setText(time);
    }

    /**
     * 隐藏触摸视图
     */
    private void _hideTouchView() {
        if (mFlTouchLayout.getVisibility() == View.VISIBLE) {
            mTvFastForward.setVisibility(View.GONE);
            mTvVolume.setVisibility(View.GONE);
            mTvBrightness.setVisibility(View.GONE);
            mFlTouchLayout.setVisibility(View.GONE);
        }
    }

    /**
     * 快进或者快退滑动改变进度，这里处理触摸滑动不是拉动 SeekBar
     *
     * @param percent 拖拽百分比
     */
    private void _onProgressSlide(float percent) {
        int position = mVideoView.getCurrentPosition();
        long duration = mVideoView.getDuration();
        // 单次拖拽最大时间差为100秒或播放时长的1/2
        long deltaMax = Math.min(100 * 1000, duration / 2);
        // 计算滑动时间
        long delta = (long) (deltaMax * percent);
        // 目标位置
        mTargetPosition = delta + position;
        if (mTargetPosition > duration) {
            mTargetPosition = duration;
        } else if (mTargetPosition <= 0) {
            mTargetPosition = 0;
        }
        int deltaTime = (int) ((mTargetPosition - position) / 1000);
        String desc;
        // 对比当前位置来显示快进或后退
        if (mTargetPosition > position) {
            desc = generateTime(mTargetPosition) + "/" + generateTime(duration) + "\n" + "+" + deltaTime + "秒";
        } else {
            desc = generateTime(mTargetPosition) + "/" + generateTime(duration) + "\n" + deltaTime + "秒";
        }
        _setFastForward(desc);
    }

    /**
     * 设置声音控制显示
     */
    private void _setVolumeInfo(int volume) {
        if (mFlTouchLayout.getVisibility() == View.GONE) {
            mFlTouchLayout.setVisibility(View.VISIBLE);
        }
        if (mTvVolume.getVisibility() == View.GONE) {
            mTvVolume.setVisibility(View.VISIBLE);
        }
        mTvVolume.setText((volume * 100 / mMaxVolume) + "%");
    }

    /**
     * 滑动改变声音大小
     */
    private void _onVolumeSlide(float percent) {
        if (mCurVolume == INVALID_VALUE) {
            mCurVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (mCurVolume < 0) {
                mCurVolume = 0;
            }
        }
        int index = (int) (percent * mMaxVolume) + mCurVolume;
        if (index > mMaxVolume) {
            index = mMaxVolume;
        } else if (index < 0) {
            index = 0;
        }
        // 变更声音
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
        // 变更进度条
        _setVolumeInfo(index);
    }


    /**
     * 递增或递减音量，量度按最大音量的 1/15
     *
     * @param isIncrease 递增或递减
     */
    private void _setVolume(boolean isIncrease) {
        int curVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (isIncrease) {
            curVolume += mMaxVolume / 15;
        } else {
            curVolume -= mMaxVolume / 15;
        }
        if (curVolume > mMaxVolume) {
            curVolume = mMaxVolume;
        } else if (curVolume < 0) {
            curVolume = 0;
        }
        // 变更声音
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, curVolume, 0);
        // 变更进度条
        _setVolumeInfo(curVolume);
        mHandler.removeCallbacks(mHideTouchViewRunnable);
        mHandler.postDelayed(mHideTouchViewRunnable, 1000);
    }

    /**
     * 设置亮度控制显示
     */
    private void _setBrightnessInfo(float brightness) {
        if (mFlTouchLayout.getVisibility() == View.GONE) {
            mFlTouchLayout.setVisibility(View.VISIBLE);
        }
        if (mTvBrightness.getVisibility() == View.GONE) {
            mTvBrightness.setVisibility(View.VISIBLE);
        }
        mTvBrightness.setText(Math.ceil(brightness * 100) + "%");
    }

    /**
     * 滑动改变亮度大小
     *
     * @param percent percent
     */
    private void _onBrightnessSlide(float percent) {
        if (!SystemBrightManager.isAutoBrightness(mAttachActivity)) {
            if (mCurBrightness < 0) {
                mCurBrightness = mAttachActivity.getWindow().getAttributes().screenBrightness;
                if (mCurBrightness < 0.0f) {
                    mCurBrightness = 0.5f;
                } else if (mCurBrightness < 0.01f) {
                    mCurBrightness = 0.01f;
                }
            }
            WindowManager.LayoutParams attributes = mAttachActivity.getWindow().getAttributes();
            attributes.screenBrightness = mCurBrightness + percent;
            if (attributes.screenBrightness > 1.0f) {
                attributes.screenBrightness = 1.0f;
            } else if (attributes.screenBrightness < 0.01f) {
                attributes.screenBrightness = 0.01f;
            }
            _setBrightnessInfo(attributes.screenBrightness);
            mAttachActivity.getWindow().setAttributes(attributes);
        }
    }

    /**
     * 手势结束调用
     */
    private void _endGesture() {
        if (mTargetPosition >= 0 && mTargetPosition != mVideoView.getCurrentPosition()) {
            // 更新视频播放进度
            seekTo((int) mTargetPosition);
            mPlayerSeek.setProgress((int) (mTargetPosition * MAX_VIDEO_SEEK / mVideoView.getDuration()));
            if (mIsEnableDanmaku) {
                mDanmakuPlayerSeek.setProgress((int) (mTargetPosition * MAX_VIDEO_SEEK / mVideoView.getDuration()));
            }
            mTargetPosition = INVALID_VALUE;
        }
        // 隐藏触摸操作显示图像
        _hideTouchView();
        _refreshHideRunnable();
        mCurVolume = INVALID_VALUE;
        mCurBrightness = INVALID_VALUE;
    }

    /**
     * ============================ 播放状态控制 ============================
     */

    // 这个用来控制弹幕启动和视频同步
    private boolean mIsRenderingStart = false;
    // 缓冲开始，这个用来控制弹幕启动和视频同步
    private boolean mIsBufferingStart = false;

    // 视频播放状态监听
    private OnInfoListener mInfoListener = new OnInfoListener() {
        @Override
        public boolean onInfo(IMediaPlayer iMediaPlayer, int status, int extra) {
            _switchStatus(status);
            if (mOutsideInfoListener != null) {
                mOutsideInfoListener.onInfo(iMediaPlayer, status, extra);
            }
            return true;
        }
    };

    /**
     * 视频播放状态处理
     */
    private void _switchStatus(int status) {
        Log.e("TTAG", "status " + status);
        switch (status) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                mIsBufferingStart = true;
                _pauseDanmaku();
                if (!mIsNeverPlay) {
                    mLoadingView.setVisibility(View.VISIBLE);
                }
            case MediaPlayerParams.STATE_PREPARING:
                break;

            case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                mIsRenderingStart = true;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                mIsBufferingStart = false;
                mLoadingView.setVisibility(View.GONE);
                // 更新进度
                mHandler.sendEmptyMessage(MSG_UPDATE_SEEK);
                if (mSkipPosition != INVALID_VALUE) {
                    _showSkipTip(); // 显示跳转提示
                }
                if (mVideoView.isPlaying()) {
                    _resumeDanmaku();   // 开启弹幕
                }
                break;

            case MediaPlayerParams.STATE_PLAYING:
                if (mIsRenderingStart && !mIsBufferingStart) {
                    _resumeDanmaku();   // 开启弹幕
                }
                break;
            case MediaPlayerParams.STATE_ERROR:
                _pauseDanmaku();
//                mCurPosition = mVideoView.getCurrentPosition();
//                mVideoView.release(false);
//                mVideoView.setRender(IjkVideoView.RENDER_TEXTURE_VIEW);
//                setVideoPath(mVideoView.getUri());
                break;

            case MediaPlayerParams.STATE_COMPLETED:
                pause();
                mIsPlayComplete = true;
                break;
        }
    }

    /**============================ Listener ============================*/

    /**
     * Register a callback to be invoked when the media file
     * is loaded and ready to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(IMediaPlayer.OnPreparedListener l) {
        mVideoView.setOnPreparedListener(l);
    }

    /**
     * Register a callback to be invoked when the end of a media file
     * has been reached during playback.
     *
     * @param l The callback that will be run
     */
    public void setOnCompletionListener(IMediaPlayer.OnCompletionListener l) {
        mVideoView.setOnCompletionListener(l);
    }

    /**
     * Register a callback to be invoked when an error occurs
     * during playback or setup.  If no listener is specified,
     * or if the listener returned false, VideoView will inform
     * the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(IMediaPlayer.OnErrorListener l) {
        mVideoView.setOnErrorListener(l);
    }

    /**
     * Register a callback to be invoked when an informational event
     * occurs during playback or setup.
     *
     * @param l The callback that will be run
     */
    public void setOnInfoListener(OnInfoListener l) {
        mOutsideInfoListener = l;
    }

    /**
     * ============================ 播放清晰度 ============================
     */

    // 默认显示/隐藏选择分辨率界面时间
    private static final int DEFAULT_QUALITY_TIME = 300;
    /**
     * 依次分别为：流畅、清晰、高清、超清和1080P
     */
    public static final int MEDIA_QUALITY_SMOOTH = 0;
    public static final int MEDIA_QUALITY_MEDIUM = 1;
    public static final int MEDIA_QUALITY_HIGH = 2;
    public static final int MEDIA_QUALITY_SUPER = 3;
    public static final int MEDIA_QUALITY_BD = 4;

    private static final int QUALITY_DRAWABLE_RES[] = new int[]{
            R.mipmap.ic_media_quality_smooth, R.mipmap.ic_media_quality_medium, R.mipmap.ic_media_quality_high,
            R.mipmap.ic_media_quality_super, R.mipmap.ic_media_quality_bd
    };
    // 保存Video Url
    private SparseArray<String> mVideoSource = new SparseArray<>();
    // 描述信息
    private String[] mMediaQualityDesc;

    // 当前选中的分辨率
    private
    @MediaQuality
    int mCurSelectQuality = MEDIA_QUALITY_SMOOTH;

    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
    @IntDef({MEDIA_QUALITY_SMOOTH, MEDIA_QUALITY_MEDIUM, MEDIA_QUALITY_HIGH, MEDIA_QUALITY_SUPER, MEDIA_QUALITY_BD})
    public @interface MediaQuality {
    }


    /**
     * 外部设置url
     *
     * @param url url
     */
    public void setOtherUrlAndPlay(String url) {
        mVideoView.release(false);
        mVideoView.setRender(IjkVideoView.RENDER_TEXTURE_VIEW);
        setVideoPath(url);
        mLoadingView.setVisibility(VISIBLE);
        start();
        seekTo(0);
    }

    /**
     * ============================ 跳转提示 ============================
     */

    // 取消跳转
    private ImageView mIvCancelSkip;
    // 跳转时间
    private TextView mTvSkipTime;
    // 执行跳转
    private TextView mTvDoSkip;
    // 跳转布局
    private View mLlSkipLayout;
    // 跳转目标时间
    private int mSkipPosition = INVALID_VALUE;

    /**
     * 跳转提示初始化
     */
    private void _initVideoSkip() {
        mLlSkipLayout = findViewById(R.id.ll_skip_layout);
        mIvCancelSkip = (ImageView) findViewById(R.id.iv_cancel_skip);
        mTvSkipTime = (TextView) findViewById(R.id.tv_skip_time);
        mTvDoSkip = (TextView) findViewById(R.id.tv_do_skip);
        mIvCancelSkip.setOnClickListener(this);
        mTvDoSkip.setOnClickListener(this);
    }

    /**
     * 返回当前进度
     */
    public int getCurPosition() {
        return mVideoView.getCurrentPosition();
    }

    /**
     * 设置跳转提示
     *
     * @param targetPosition 目标进度,单位:ms
     */
    public IjkPlayerView setSkipTip(int targetPosition) {
        if (0 != targetPosition) {
            mSkipPosition = targetPosition;
        }
        return this;
    }

    /**
     * 显示跳转提示
     */
    private void _showSkipTip() {
        if (0 == mSkipPosition) {
            return;
        }
        if (mSkipPosition != INVALID_VALUE && mLlSkipLayout.getVisibility() == GONE) {
            mLlSkipLayout.setVisibility(VISIBLE);
            mTvSkipTime.setText(generateTime(mSkipPosition));
            AnimHelper.doSlideRightIn(mLlSkipLayout, mWidthPixels, 0, 800);
            mHandler.postDelayed(mHideSkipTipRunnable, DEFAULT_HIDE_TIMEOUT * 2);
        }
    }

    /**
     * 隐藏跳转提示
     */
    private void _hideSkipTip() {
        if (mLlSkipLayout.getVisibility() == GONE) {
            return;
        }
        ViewCompat.animate(mLlSkipLayout).translationX(-mLlSkipLayout.getWidth()).alpha(0).setDuration(500)
                .setListener(new ViewPropertyAnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(View view) {
                        mLlSkipLayout.setVisibility(GONE);
                    }
                }).start();
        mSkipPosition = INVALID_VALUE;
    }

    /**
     * 隐藏跳转提示线程
     */
    private Runnable mHideSkipTipRunnable = new Runnable() {
        @Override
        public void run() {
            _hideSkipTip();
        }
    };

    /**
     * ============================ 弹幕 ============================
     */

    /**
     * 视频编辑状态：正常未编辑状态、在播放时编辑、暂停时编辑
     */
    private static final int NORMAL_STATUS = 501;
    private static final int INTERRUPT_WHEN_PLAY = 502;
    private static final int INTERRUPT_WHEN_PAUSE = 503;

    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.FIELD)
    @IntDef({NORMAL_STATUS, INTERRUPT_WHEN_PLAY, INTERRUPT_WHEN_PAUSE})
    @interface VideoStatus {
    }

    private
    @VideoStatus
    int mVideoStatus = NORMAL_STATUS;

    // 弹幕开源控件
    private IDanmakuView mDanmakuView;
    // 弹幕显示/隐藏按钮
    private ImageView mIvDanmakuControl;
    // 弹幕编辑布局打开按钮
    private TextView mTvOpenEditDanmaku;
    // 使能弹幕才会显示的播放进度条
    private SeekBar mDanmakuPlayerSeek;
    // 使能弹幕才会显示时间分割线
    private TextView mTvTimeSeparator;
    // 弹幕编辑布局
    private View mEditDanmakuLayout;
    // 弹幕内容编辑框
    private EditText mEtDanmakuContent;
    // 取消弹幕发送
    private ImageView mIvCancelSend;
    // 发送弹幕
    private ImageView mIvDoSend;

    // 弹幕基础设置布局
    private View mDanmakuOptionsBasic;
    // 弹幕字体大小选项卡
    private RadioGroup mDanmakuTextSizeOptions;
    // 弹幕类型选项卡
    private RadioGroup mDanmakuTypeOptions;
    // 弹幕当前颜色
    private RadioButton mDanmakuCurColor;
    // 开关弹幕颜色选项卡
    private ImageView mDanmakuMoreColorIcon;
    // 弹幕更多颜色设置布局
    private View mDanmakuMoreOptions;
    // 弹幕颜色选项卡
    private RadioGroup mDanmakuColorOptions;

    // 弹幕控制相关
    private DanmakuContext mDanmakuContext;
    // 弹幕解析器
    private BaseDanmakuParser mParser;
    // 是否使能弹幕
    private boolean mIsEnableDanmaku = false;
    // 弹幕颜色
    private int mDanmakuTextColor = Color.WHITE;
    // 弹幕字体大小
    private float mDanmakuTextSize = INVALID_VALUE;
    // 弹幕类型
    private int mDanmakuType = BaseDanmaku.TYPE_SCROLL_RL;
    // 弹幕基础设置布局的宽度
    private int mBasicOptionsWidth = INVALID_VALUE;
    // 弹幕更多颜色设置布局宽度
    private int mMoreOptionsWidth = INVALID_VALUE;
    // 弹幕要跳转的目标位置，等视频播放再跳转，不然老出现只有弹幕在动的情况
    private long mDanmakuTargetPosition = INVALID_VALUE;

    /**
     * 装载弹幕，在视频按了播放键才装载
     */
    private void _loadDanmaku() {
        if (mIsEnableDanmaku) {
            // 设置弹幕
            mDanmakuContext = DanmakuContext.create();
            if (mParser == null) {
                mParser = new BaseDanmakuParser() {
                    @Override
                    protected Danmakus parse() {
                        return new Danmakus();
                    }
                };
            }
            mDanmakuView.enableDanmakuDrawingCache(true);
            mDanmakuView.prepare(mParser, mDanmakuContext);
        }
    }

    /**
     * 显示/隐藏弹幕
     *
     * @param isShow 是否显示
     * @return
     */
    public IjkPlayerView showOrHideDanmaku(boolean isShow) {
        if (isShow) {
            mIvDanmakuControl.setSelected(false);
            mDanmakuView.show();
        } else {
            mIvDanmakuControl.setSelected(true);
            mDanmakuView.hide();
        }
        return this;
    }

    /**
     * @return 是否从编辑状态回退
     */
    public boolean recoverFromEditVideo() {
        if (mVideoStatus == NORMAL_STATUS) {
            return false;
        }
        if (mIsFullscreen) {
            _recoverScreen();
        }
        if (mVideoStatus == INTERRUPT_WHEN_PLAY) {
            start();
        }
        mVideoStatus = NORMAL_STATUS;
        return true;
    }

    /**
     * 激活弹幕
     */
    private void _resumeDanmaku() {
        if (mDanmakuView != null && mDanmakuView.isPrepared() && mDanmakuView.isPaused()) {
            if (mDanmakuTargetPosition != INVALID_VALUE) {
                mDanmakuView.seekTo(mDanmakuTargetPosition);
                mDanmakuTargetPosition = INVALID_VALUE;
            } else {
                mDanmakuView.resume();
            }
        }
    }

    /**
     * 暂停弹幕
     */
    private void _pauseDanmaku() {
        if (mDanmakuView != null && mDanmakuView.isPrepared()) {
            mDanmakuView.pause();
        }
    }

    /**
     * 切换弹幕的显示/隐藏
     */
    private void _toggleDanmakuShow() {
        if (mIvDanmakuControl.isSelected()) {
            showOrHideDanmaku(true);
        } else {
            showOrHideDanmaku(false);
        }
    }

    /**
     * 切换弹幕相关控件View的显示/隐藏
     *
     * @param isShow 是否显示
     */
    private void _toggleDanmakuView(boolean isShow) {
        if (mIsEnableDanmaku) {
            if (isShow) {
                mIvDanmakuControl.setVisibility(VISIBLE);
                mTvOpenEditDanmaku.setVisibility(VISIBLE);
                mTvTimeSeparator.setVisibility(VISIBLE);
                mDanmakuPlayerSeek.setVisibility(VISIBLE);
                mPlayerSeek.setVisibility(GONE);
            } else {
                mIvDanmakuControl.setVisibility(GONE);
                mTvOpenEditDanmaku.setVisibility(GONE);
                mTvTimeSeparator.setVisibility(GONE);
                mDanmakuPlayerSeek.setVisibility(GONE);
                mPlayerSeek.setVisibility(VISIBLE);
            }
        }

    }

    /**
     * 从弹幕编辑状态复原界面
     */
    private void _recoverScreen() {
        // 清除焦点
        mEditDanmakuLayout.clearFocus();
        mEditDanmakuLayout.setVisibility(GONE);
        // 关闭软键盘
        SoftInputUtils.closeSoftInput(mAttachActivity);
        // 重新设置全屏界面UI标志位
        _setUiLayoutFullscreen();
        if (mDanmakuColorOptions.getWidth() != 0) {
            _toggleMoreColorOptions();
        }
    }

    /**
     * 动画切换弹幕颜色选项卡显示
     */
    private void _toggleMoreColorOptions() {
        if (mBasicOptionsWidth == INVALID_VALUE) {
            mBasicOptionsWidth = mDanmakuOptionsBasic.getWidth();
        }
        if (mDanmakuColorOptions.getWidth() == 0) {
            AnimHelper.doClipViewWidth(mDanmakuOptionsBasic, mBasicOptionsWidth, 0, 300);
            AnimHelper.doClipViewWidth(mDanmakuColorOptions, 0, mMoreOptionsWidth, 300);
            ViewCompat.animate(mDanmakuMoreColorIcon).rotation(180).setDuration(150).setStartDelay(250).start();
        } else {
            AnimHelper.doClipViewWidth(mDanmakuOptionsBasic, 0, mBasicOptionsWidth, 300);
            AnimHelper.doClipViewWidth(mDanmakuColorOptions, mMoreOptionsWidth, 0, 300);
            ViewCompat.animate(mDanmakuMoreColorIcon).rotation(0).setDuration(150).setStartDelay(250).start();
        }
    }

    /**
     * ============================ 电量、时间、锁屏、截屏 ============================
     */


    // 锁屏状态广播接收器
    private ScreenBroadcastReceiver mScreenReceiver;
    // 判断是否出现锁屏,有则需要重新设置渲染器，不然视频会没有动画只有声音
    private boolean mIsScreenLocked = false;


    /**
     * 锁屏
     */
    private void _initReceiver() {
        mScreenReceiver = new ScreenBroadcastReceiver();
        mAttachActivity.registerReceiver(mScreenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    /**
     * 锁屏状态广播接收者
     */
    private class ScreenBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                mIsScreenLocked = true;
            }
        }
    }

    /**
     * 获取当前时间
     *
     * @return
     */
    public int getCurrTime() {
        if (mVideoView == null || mIsSeeking) {
            return 0;
        }
        return mVideoView.getCurrentPosition();
    }

    /**
     * 获取视频总时长
     *
     * @return
     */
    public int getDuration() {
        if (mVideoView == null) {
            return 0;
        }
        return mVideoView.getDuration();
    }

    /**
     * 获取缓冲进度
     *
     * @return
     */
    public int getBufferPosition() {
        if (mVideoView == null) {
            return 0;
        }
        return mVideoView.getBufferPercentage() * 10;
    }

    /**
     * 获取当前进度
     *
     * @return
     */
    public int getCurrPosition() {
        if (mVideoView == null) {
            return 0;
        }
        // 视频播放的当前进度
        int position = mVideoView.getCurrentPosition();
        // 视频总的时长
        int duration = mVideoView.getDuration();
        long pos = 0;
        if (duration > 0) {
            // 转换为 Seek 显示的进度值
            pos = (long) MAX_VIDEO_SEEK * position / duration;
        }
        return (int) pos;
    }

    /**
     * 不完全退出状态
     *
     * @return 0为不必保存
     */
    public int isUncompletedBack() {
        if (mVideoView.getCurrentPosition() == 0 || Math.abs(mVideoView.getCurrentPosition() - mVideoView.getDuration()) < 10000) {
            return 0;
        } else {
            return mVideoView.getCurrentPosition();
        }
    }

    /**
     * 隐藏所有按钮
     */
    public void hideAllViews() {
        isHideAll = true;
        _hideAllView(true);
        FrameLayout mVideoBack = (FrameLayout) findViewById(R.id.fl_video_box);
        mVideoBack.setBackgroundColor(Color.WHITE);
        RelativeLayout rl_loading = (RelativeLayout) findViewById(R.id.rl_loading);
        rl_loading.setVisibility(View.GONE);
    }

    private boolean isStudy = true;
    private boolean isHideAll = false;

    /**
     * 显示栏目
     */
    public void showContorlBar(boolean isStudy) {
        this.isStudy = isStudy;
        if (!mIsShowBar) {
            _toggleControlBar();
        } else {
            mHandler.removeCallbacks(mHideBarRunnable);
            mHandler.postDelayed(mHideBarRunnable, DEFAULT_HIDE_TIMEOUT);
            mHandler.sendEmptyMessage(MSG_UPDATE_SEEK);
        }
        if (isStudy) {
            mRlPlay.setVisibility(View.VISIBLE);
            mRlExpand.setVisibility(View.VISIBLE);
            mRlCE.setVisibility(View.VISIBLE);
            mPlayerSeek.setEnabled(true);
            mPlayerSeek.setThumb(ContextCompat.getDrawable(mAttachActivity, R.drawable.video_btn_point));
        } else {
            mRlPlay.setVisibility(View.GONE);
            mRlExpand.setVisibility(View.GONE);
            mRlCE.setVisibility(View.GONE);
            mPlayerSeek.setEnabled(false);
            mPlayerSeek.setThumb(null);
        }
    }
}
