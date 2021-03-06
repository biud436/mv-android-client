/*
 * Copyright (c) 2017-2019 Altimit Community Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or imp
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package systems.altimit.rpgmakermv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.opengl.Visibility;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdCallback;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import static com.google.android.gms.ads.AdRequest.ERROR_CODE_NETWORK_ERROR;

/**
 * Created by felixjones on 28/04/2017.
 */
public class WebPlayerActivity extends Activity {

    private static final String TOUCH_INPUT_ON_CANCEL = "TouchInput._onCancel();";

    private Player mPlayer;
    private AlertDialog mQuitDialog;
    private int mSystemUiVisibility;

    /**
     * 프레임 레이아웃 추가
     */
    public static FrameLayout ROOT_LAYOUT = null;
    public static WebPlayerActivity WEBPLAYER_ACTIVITY = null;
    public static AdView WEBPLAYER_ADVIEW = null;

    public AdRequest mAdRequest;
    public InterstitialAd mInterstitialAd;
    public RewardedAd mRewardedAd;

    @SuppressLint("ObsoleteSdkInt")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BuildConfig.BACK_BUTTON_QUITS) {
            createQuitDialog();
        }

        mSystemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mSystemUiVisibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            mSystemUiVisibility |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            mSystemUiVisibility |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            mSystemUiVisibility |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mSystemUiVisibility |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
        }

        // Remote debugging...
        if(BuildConfig.DEBUG && BuildConfig.REMOTE_DEBUGGING) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }

        mPlayer = PlayerHelper.create(this);

        mPlayer.setKeepScreenOn();

        if (!addBootstrapInterface(mPlayer)) {
            Uri.Builder projectURIBuilder = Uri.fromFile(new File(getString(R.string.mv_project_index))).buildUpon();
            Bootstrapper.appendQuery(projectURIBuilder, getString(R.string.query_noaudio));
            if (BuildConfig.SHOW_FPS) {
                Bootstrapper.appendQuery(projectURIBuilder, getString(R.string.query_showfps));
            }
            mPlayer.loadUrl(projectURIBuilder.build().toString());
        }

        // 애드몹을 초기화합니다.
        init();

    }

    /**
     * 애드몹을 초기화합니다.
     */
    public void init() {

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {

            }
        });

        WEBPLAYER_ACTIVITY = this;

        // Main Layout 설정
        ViewGroup.LayoutParams framelayoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        // 프레임 레이아웃 생성한다.
        ROOT_LAYOUT = null;
        ROOT_LAYOUT = new FrameLayout(this);

        ROOT_LAYOUT.setLayoutParams(framelayoutParams);

        // 웹 플레이어의 레이아웃 속성을 설정한다.
        FrameLayout.LayoutParams webViewLayoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);

        // 프레임 레이아웃에 웹뷰를 추가한다.
        ROOT_LAYOUT.addView(mPlayer.getView(), webViewLayoutParams);

        // 애드뷰 레이아웃(AdView Layout)을 설정한다.
        FrameLayout.LayoutParams adViewLayoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);

        // 광고의 위치를 화면 하단으로 설정한다.
        adViewLayoutParams.gravity = Gravity.BOTTOM;

        // 디버그 모드라면 테스트 광고가 나오게 합니다.
        List<String> testDevices = new ArrayList<>();
        testDevices.add(AdRequest.DEVICE_ID_EMULATOR);

        RequestConfiguration.Builder requestConfigurationBuilder = new RequestConfiguration.Builder();

        if(BuildConfig.DEBUG) {
            requestConfigurationBuilder.setTestDeviceIds(testDevices);
        }

        RequestConfiguration requestConfiguration = requestConfigurationBuilder.build();

        MobileAds.setRequestConfiguration(requestConfiguration);

        // 애드몹을 초기화합니다.
        WEBPLAYER_ADVIEW = null;
        WEBPLAYER_ADVIEW = new AdView(this);
        WEBPLAYER_ADVIEW.setLayoutParams(adViewLayoutParams);
        WEBPLAYER_ADVIEW.setAdSize(AdSize.BANNER);
        WEBPLAYER_ADVIEW.setAdUnitId(getString(R.string.BannerAd));

        mAdRequest = new AdRequest.Builder().build();

        // 애드몹이 화면에서 가려진 상태로 생성된다.
        WEBPLAYER_ADVIEW.setVisibility(View.INVISIBLE);

        WEBPLAYER_ADVIEW.loadAd(mAdRequest);

        setContentView(ROOT_LAYOUT);

        // 프레임 레이아웃에 애드몹을 추가한다.
        ROOT_LAYOUT.addView(WEBPLAYER_ADVIEW);
        ROOT_LAYOUT.bringToFront();
        ROOT_LAYOUT.requestLayout();

        // 전면 광고를 생성합니다.
        mInterstitialAd = initWithInterstitialAd();

        // 보상형 광고를 생성합니다.
        mRewardedAd = initWithRewardedAd();


    }

    private void addCoin(int coins) {
        mPlayer.evaluateJavascript(String.format("if($gameParty) { $gameParty.gainGold(%i); }", coins));
    }

    /**
     * 이 메소드는 보상형 광고 인스턴스를 생성합니다.
     * 그러나 광고를 로드하거나 화면에 띄우진 않습니다.
     */
    public RewardedAd initWithRewardedAd() {
        RewardedAd rewardedAd = new RewardedAd(this, getString(R.string.VideoAd));

        RewardedAdLoadCallback adLoadCallback = new RewardedAdLoadCallback() {

            /**
             * 	이 메소드는 광고 로드가 완료되면 실행됩니다.
             */
            @Override
            public void onRewardedAdLoaded() {

                // 디버그 모드로 빌드 되었을 때, 다음 토스트를 화면에 띄웁니다.
                if(BuildConfig.DEBUG) {
                    Toast.makeText(WebPlayerActivity.this, "onRewardedAdLoaded", Toast.LENGTH_SHORT).show();
                }

            }

            /**
             * 이 메소드는 광고 로드에 실패할 때 실행됩니다. 이 메소드에는 발생한 오류 유형을 나타내는 errorCode 매개변수가 포함됩니다. 가능한 값이 AdRequest 클래스의 상수로 정의됩니다.
             * ERROR_CODE_INTERNAL_ERROR: 광고 서버에서 잘못된 응답을 받는 등 내부적으로 오류가 발생했다는 의미입니다.
             * ERROR_CODE_INVALID_REQUEST: 광고 단위 ID가 잘못된 경우처럼 광고 요청이 잘못되었다는 의미입니다.
             * ERROR_CODE_NETWORK_ERROR: 네트워크 연결로 인해 광고 요청에 성공하지 못했다는 의미입니다.
             * ERROR_CODE_NO_FILL: 광고 요청에는 성공했지만 광고 인벤토리의 부족으로 광고가 반환되지 않았다는 의미입니다.
             * @param errorCode
             */
            @Override
            public void onRewardedAdFailedToLoad(int errorCode) {
                // 디버그 모드로 빌드 되었을 때, 다음 토스트를 화면에 띄웁니다.
                if(BuildConfig.DEBUG) {
                    Toast.makeText(WebPlayerActivity.this, String.format("onRewardedAdFailedToLoad => errorCode : %i", errorCode), Toast.LENGTH_SHORT).show();
                }
            }
        };
        rewardedAd.loadAd(new AdRequest.Builder().build(), adLoadCallback);

        return rewardedAd;
    }

    /**
     * 보상형 광고를 화면에 띄웁니다.
     */
    public void showRewardedAd() {

        if(mRewardedAd.isLoaded()) {
            mRewardedAd.show(this, new RewardedAdCallback() {

                /**
                 * 이 메소드는 광고가 표시될 때 실행되며 기기 화면을 덮습니다.
                 */
                public void onRewardedAdOpened() {
                    // 디버그 모드로 빌드 되었을 때, 다음 토스트를 화면에 띄웁니다.
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(WebPlayerActivity.this, "onRewardedAdOpened()", Toast.LENGTH_SHORT).show();
                    }
                }

                public void onRewardedAdClosed() {
                    mRewardedAd = initWithRewardedAd();

                    // 디버그 모드로 빌드 되었을 때, 다음 토스트를 화면에 띄웁니다.
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(WebPlayerActivity.this, "onRewardedAdClosed()", Toast.LENGTH_SHORT).show();
                    }
                }

                /**
                 * 이 메소드는 사용자가 광고와 상호작용하여 보상을 받아야 할 때 실행됩니다.
                 * 광고 단위에 대해 설정된 보상 관련 세부정보는 RewardItem 매개변수의 getType() 및 getAmount() 메소드를 통해 액세스할 수 있습니다.
                 *
                 * @param reward
                 */
                public void onUserEarnedReward(@NonNull RewardItem reward) {
                    // 디버그 모드로 빌드 되었을 때, 다음 토스트를 화면에 띄웁니다.
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(WebPlayerActivity.this, "onUserEarnedReward()", Toast.LENGTH_SHORT).show();
                    }
                    // 게임 내 골드를 취득합니다.
                    addCoin(reward.getAmount());
                }

                /**
                 * 이 메소드는 광고 표시에 실패할 때 실행됩니다.
                 * 이 메소드에는 발생한 오류 유형을 나타내는 errorCode 매개변수가 포함됩니다.
                 * 가능한 값이 RewardedAdCallback 클래스의 상수로 정의됩니다.
                 *
                 * @param errorCode
                 */
                public void onRewardedAdFailedToShow(int errorCode) {
                    switch(errorCode) {
                        case AdRequest.ERROR_CODE_NETWORK_ERROR:
                            if (BuildConfig.DEBUG) Toast.makeText(WebPlayerActivity.this, "onRewardedAdFailedToShow:ERROR_CODE_NETWORK_ERROR", Toast.LENGTH_SHORT).show();
                            break;
                        case AdRequest.ERROR_CODE_INTERNAL_ERROR:
                            if (BuildConfig.DEBUG) Toast.makeText(WebPlayerActivity.this, "onRewardedAdFailedToShow:ERROR_CODE_INTERNAL_ERROR", Toast.LENGTH_SHORT).show();
                            break;
                        case AdRequest.ERROR_CODE_INVALID_REQUEST:
                            if (BuildConfig.DEBUG) Toast.makeText(WebPlayerActivity.this, "onRewardedAdFailedToShow:ERROR_CODE_INVALID_REQUEST", Toast.LENGTH_SHORT).show();
                            break;
                        case AdRequest.ERROR_CODE_NO_FILL:
                            if (BuildConfig.DEBUG) Toast.makeText(WebPlayerActivity.this, "onRewardedAdFailedToShow:ERROR_CODE_NO_FILL", Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            });
        } else {

        }
    }

    /**
     * 전면 광고를 생성합니다.
     * 현재 모바일 SDK가 프레임 레이아웃과의 버그로 인해 클릭이 되지 않는 문제가 있습니다.
     * @return
     */
    private InterstitialAd initWithInterstitialAd() {
        InterstitialAd interstitialAd = new InterstitialAd(this);
        interstitialAd.setAdUnitId(getString(R.string.InterstitialAd));

        interstitialAd.setAdListener(new AdListener() {

            @Override
            public void onAdLoaded() {
                // 전면 광고를 띄웁니다.
                if(mInterstitialAd.isLoaded())
                    mInterstitialAd.show();

                // 디버그 모드일 때 토스트를 화면에 띄웁니다.
                if(BuildConfig.DEBUG) {
                    Toast.makeText(WebPlayerActivity.this, "onAdLoaded()", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAdFailedToLoad(int errorCode) {
                // 디버그 모드일 때 토스트를 화면에 띄웁니다.
                if(BuildConfig.DEBUG) {
                    Toast.makeText(WebPlayerActivity.this, "onAdFailedToLoad()", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onAdClosed() {
                // 디버그 모드일 때 토스트를 화면에 띄웁니다.
                if(BuildConfig.DEBUG) {
                    Toast.makeText(WebPlayerActivity.this, "onAdClosed()", Toast.LENGTH_SHORT).show();
                }

            }
        });

        return interstitialAd;

    }

    public void loadInterstitial() {
        mInterstitialAd.loadAd(new AdRequest.Builder().build());
    }

    @Override
    public void onBackPressed() {
        if (BuildConfig.BACK_BUTTON_QUITS) {
            if (mQuitDialog != null) {
                mQuitDialog.show();
            } else {
                super.onBackPressed();
            }
        } else {
            mPlayer.evaluateJavascript(TOUCH_INPUT_ON_CANCEL);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onPause() {

        // 2020.04.12
        // pauseTimers();는 웹뷰 뿐만 아니라 전역에 적용되므로
        // 아래 구문이 있으면 광고를 클릭할 수 없게 됩니다.
        // link : https://stackoverflow.com/a/54371919
        // mPlayer.pauseTimers();

        mPlayer.onHide();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().setSystemUiVisibility(mSystemUiVisibility);
        if (mPlayer != null) {
            // mPlayer.resumeTimers();
            mPlayer.onShow();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(WEBPLAYER_ADVIEW != null) {
            WEBPLAYER_ADVIEW.destroy();
        }

        mPlayer.onDestroy();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    private void createQuitDialog() {
        String appName = getString(R.string.app_name);
        String[] quitLines = getResources().getStringArray(R.array.quit_message);
        StringBuilder quitMessage = new StringBuilder();
        for (int ii = 0; ii < quitLines.length; ii++) {
            quitMessage.append(quitLines[ii].replace("$1", appName));
            if (ii < quitLines.length - 1) {
                quitMessage.append("\n");
            }
        }

        if (quitMessage.length() > 0) {
            mQuitDialog = new AlertDialog.Builder(this)
                    .setPositiveButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            getWindow().getDecorView().setSystemUiVisibility(mSystemUiVisibility);
                        }
                    })
                    .setNegativeButton("Quit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            WebPlayerActivity.super.onBackPressed();
                        }
                    })
                    .setMessage(quitMessage.toString())
                    .create();
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private static boolean addBootstrapInterface(Player player) {
        if (BuildConfig.BOOTSTRAP_INTERFACE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            new Bootstrapper(player);
            return true;
        }
        return false;
    }

    /**
     *
     */
    private static final class Bootstrapper extends PlayerHelper.Interface implements Runnable {

        private static Uri.Builder appendQuery(Uri.Builder builder, String query) {
            Uri current = builder.build();
            String oldQuery = current.getEncodedQuery();
            if (oldQuery != null && oldQuery.length() > 0) {
                query = oldQuery + "&" + query;
            }
            return builder.encodedQuery(query);
        }

        private static final String INTERFACE = "boot";
        private static final String PREPARE_FUNC = "prepare( webgl(), webaudio(), false )";

        private Player mPlayer;
        private Uri.Builder mURIBuilder;

        private Bootstrapper(Player player) {
            Context context = player.getContext();
            player.addJavascriptInterface(this, Bootstrapper.INTERFACE);

            mPlayer = player;
            mURIBuilder = Uri.fromFile(new File(context.getString(R.string.mv_project_index))).buildUpon();
            mPlayer.loadData(context.getString(R.string.webview_default_page));
        }

        @Override
        protected void onStart() {
            Context context = mPlayer.getContext();
            final String code = new String(Base64.decode(context.getString(R.string.webview_detection_source), Base64.DEFAULT), Charset.forName("UTF-8")) + INTERFACE + "." + PREPARE_FUNC + ";";
            mPlayer.post(new Runnable() {
                @Override
                public void run() {
                    mPlayer.evaluateJavascript(code);
                }
            });
        }

        @Override
        protected void onPrepare(boolean webgl, boolean webaudio, boolean showfps) {
            Context context = mPlayer.getContext();
            if (webgl && !BuildConfig.FORCE_CANVAS) {
                mURIBuilder = appendQuery(mURIBuilder, context.getString(R.string.query_webgl));
            } else {
                mURIBuilder = appendQuery(mURIBuilder, context.getString(R.string.query_canvas));
            }
            if (!webaudio || BuildConfig.FORCE_NO_AUDIO) {
                mURIBuilder = appendQuery(mURIBuilder, context.getString(R.string.query_noaudio));
            }
            if (showfps || BuildConfig.SHOW_FPS) {
                mURIBuilder = appendQuery(mURIBuilder, context.getString(R.string.query_showfps));
            }
            mPlayer.post(this);
        }

        @Override
        public void run() {
            mPlayer.removeJavascriptInterface(INTERFACE);
            mPlayer.loadUrl(mURIBuilder.build().toString());
        }

    }

}