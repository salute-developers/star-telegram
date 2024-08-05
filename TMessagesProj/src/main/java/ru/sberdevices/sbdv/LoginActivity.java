/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package ru.sberdevices.sbdv;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.R;
import org.telegram.messenger.SRPHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.HintEditText;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.SlideView;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LaunchLayout;
import org.telegram.ui.TwoStepVerificationActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import kotlin.Unit;
import kotlinx.coroutines.Dispatchers;
import ru.sberdevices.sbdv.analytics.AnalyticsCollector;
import ru.sberdevices.sbdv.auth.view.QrLoginView;
import ru.sberdevices.sbdv.auth.view.QrLoginViewController;
import ru.sberdevices.sbdv.config.Config;
import ru.sberdevices.sbdv.model.AppEvent;
import ru.sberdevices.sbdv.notifications.StarNotifications;
import ru.sberdevices.sbdv.util.DeviceUtils;
import ru.sberdevices.sbdv.util.TelegramDimensions;
import ru.sberdevices.telegramcalls.vendor.authorization.domain.AuthorizationFeature;

import static android.view.View.inflate;

@SuppressLint("HardwareIds")
public class LoginActivity extends BaseFragment {

    public static final class SbdvPhoneLoginDimensions {
        public static final int phoneLoginViewWidth = TelegramDimensions.selectSize(350,456);
        public static final int phoneLoginViewHeight = TelegramDimensions.selectSize(336,456);
    }

    private final static String TAG = "LoginActivity";

    public final static boolean ENABLE_PASTED_TEXT_PROCESSING = false;
    private final static int SHOW_DELAY = SharedConfig.getDevicePerformanceClass() <= SharedConfig.PERFORMANCE_CLASS_AVERAGE ? 150 : 100;

    public final static int AUTH_TYPE_MESSAGE = 1,
            AUTH_TYPE_SMS = 2,
            AUTH_TYPE_FLASH_CALL = 3,
            AUTH_TYPE_CALL = 4,
            AUTH_TYPE_MISSED_CALL = 11,
            AUTH_TYPE_FRAGMENT_SMS = 15;

    private final static int VIEW_PHONE_INPUT = 0,
            VIEW_CODE_MESSAGE = 1,
            VIEW_CODE_SMS = 2,
            VIEW_CODE_FLASH_CALL = 3,
            VIEW_CODE_CALL = 4,
            VIEW_REGISTER = 5,
            VIEW_PASSWORD = 6,
            VIEW_RECOVER = 7,
            VIEW_RESET_WAIT = 8,
            VIEW_NEW_PASSWORD_STAGE_1 = 9,
            VIEW_NEW_PASSWORD_STAGE_2 = 10,
            VIEW_CODE_MISSED_CALL = 11,
            VIEW_ADD_EMAIL = 12,
            VIEW_CODE_EMAIL_SETUP = 13,
            VIEW_CODE_EMAIL = 14,
            VIEW_CODE_FRAGMENT_SMS = 15;

    public final static int COUNTRY_STATE_NOT_SET_OR_VALID = 0,
            COUNTRY_STATE_EMPTY = 1,
            COUNTRY_STATE_INVALID = 2;

    private @interface CountryState {}

    private int currentViewNum;
    private SlideView[] views = new SlideView[11];

    private boolean restoringState;

    private Dialog permissionsDialog;
    private Dialog permissionsShowDialog;
    private ArrayList<String> permissionsItems = new ArrayList<>();
    private ArrayList<String> permissionsShowItems = new ArrayList<>();
    private boolean checkPermissions = true;
    private boolean checkShowPermissions = true;
    private boolean newAccount;
    private boolean syncContacts = true;
    private boolean testBackend = false;

    private int scrollHeight;

    private int currentDoneType;
    private AnimatorSet[] showDoneAnimation = new AnimatorSet[2];
    private ActionBarMenuItem doneItem;
    private AnimatorSet doneItemAnimation;
    private ContextProgressView doneProgressView;
    private ImageView floatingButtonIcon;
    private FrameLayout floatingButtonContainer;
    private RadialProgressView floatingProgressView;
    private int progressRequestId;
    private boolean[] doneButtonVisible = new boolean[] {true, false};

    private static final int DONE_TYPE_FLOATING = 0;
    private static final int DONE_TYPE_ACTION = 1;

    private final static int done_button = 1;

    private AnalyticsCollector analyticsCollector = SbdvServiceLocator.getAnalyticsSdkSharedInstance();
    private boolean needRequestPermissions;

    @Nullable
    private QrLoginViewController qrLoginViewController;

    private static class ProgressView extends View {

        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final RectF boundsRect = new RectF();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);

        private long startTime;
        private long duration;
        private boolean animating;

        private float radius;

        public ProgressView(Context context) {
            super(context);
            paint.setColor(Theme.getColor(Theme.key_login_progressInner));
            paint2.setColor(Theme.getColor(Theme.key_login_progressOuter));
        }

        public void startProgressAnimation(long duration) {
            this.animating = true;
            this.duration = duration;
            this.startTime = System.currentTimeMillis();
            invalidate();
        }

        public void resetProgressAnimation() {
            duration = 0;
            startTime = 0;
            animating = false;
            invalidate();
        }

        public boolean isProgressAnimationRunning() {
            return animating;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            path.rewind();
            radius = h / 2f;
            boundsRect.set(0, 0, w, h);
            rect.set(boundsRect);
            path.addRoundRect(boundsRect, radius, radius, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float progress;
            if (duration > 0) {
                progress = Math.min(1f, (System.currentTimeMillis() - startTime) / (float) duration);
            } else {
                progress = 0f;
            }

            canvas.clipPath(path);
            canvas.drawRoundRect(boundsRect, radius, radius, paint);
            rect.right = boundsRect.right * progress;
            canvas.drawRoundRect(rect, radius, radius, paint2);

            if (animating &= duration > 0 && progress < 1f) {
                postInvalidateOnAnimation();
            }
        }
    }

    public LoginActivity() {
        super();
        analyticsCollector.onAppEvent(AppEvent.OPEN_LOGIN_SCREEN);
    }

    public LoginActivity(int account) {
        super();
        currentAccount = account;
        newAccount = true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        Log.d(TAG, "onFragmentDestroy()@"+this.hashCode());

        for (int a = 0; a < views.length; a++) {
            if (views[a] != null) {
                views[a].onDestroyActivity();
            }
        }

        if (qrLoginViewController != null) qrLoginViewController.onDestroy();
    }

    @Override
    public View createView(Context context) {
        Log.d(TAG, "createView()@"+this.hashCode());

        actionBar.setTitle(LocaleController.getString("AppName", R.string.AppName));
        actionBar.setBackgroundResource(R.drawable.intro_frame_actionbar_background_alpha20);
        actionBar.setFocusable(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == done_button) {
                    onDoneButtonPressed();
                } else if (id == -1) {
                    if (onBackPressed()) {
                        finishFragment();
                    }
                }
            }
        });

        currentDoneType = DONE_TYPE_FLOATING;
        doneButtonVisible[DONE_TYPE_FLOATING] = true;
        doneButtonVisible[DONE_TYPE_ACTION] = false;

        ActionBarMenu menu = actionBar.createMenu();
        actionBar.setAllowOverlayTitle(true);
        doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
        doneProgressView = new ContextProgressView(context, 1);
        doneProgressView.setAlpha(0.0f);
        doneProgressView.setScaleX(0.1f);
        doneProgressView.setScaleY(0.1f);
        doneProgressView.setVisibility(View.INVISIBLE);
        doneItem.setAlpha(0.0f);
        doneItem.setScaleX(0.1f);
        doneItem.setScaleY(0.1f);
        doneItem.addView(doneProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        doneItem.setContentDescription(LocaleController.getString("Done", R.string.Done));
        doneItem.setVisibility(doneButtonVisible[DONE_TYPE_ACTION] ? View.VISIBLE : View.GONE);

        FrameLayout container = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                MarginLayoutParams marginLayoutParams = (MarginLayoutParams) floatingButtonContainer.getLayoutParams();
                if (Bulletin.getVisibleBulletin() != null && Bulletin.getVisibleBulletin().isShowing()) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    marginLayoutParams.bottomMargin = AndroidUtilities.dp(14) + Bulletin.getVisibleBulletin().getLayout().getMeasuredHeight() - AndroidUtilities.dp(10);
                } else {
                    marginLayoutParams.bottomMargin = AndroidUtilities.dp(14);
                }

                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        container.setBackgroundResource(R.drawable.login_frame_background);
        int padding = AndroidUtilities.dp(TelegramDimensions.selectSize(14, 20));
        container.setPadding(padding,padding,padding,padding);
        fragmentView = container;

        FrameLayout phoneLoginContainer = new FrameLayout(context);
        container.addView(phoneLoginContainer);

        final FrameLayout finalContainer = container;
        QrLoginView qrLoginView = new QrLoginView(context, (view) -> {
            view.setVisibility(View.GONE);
            phoneLoginContainer.setVisibility(View.VISIBLE);

            finalContainer.setPadding(padding, padding, padding, padding);
            finalContainer.setFitsSystemWindows(false);

            parentLayout.getView().setBackgroundResource(R.drawable.intro_frame_background);

            LaunchLayout launchLayout = (LaunchLayout) parentLayout.getView().getParent();
            launchLayout.setFullscreenMode(false);

            return Unit.INSTANCE;
        });
        container.addView(qrLoginView);

        container = phoneLoginContainer;

        ScrollView scrollView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                if (currentViewNum == VIEW_CODE_MESSAGE || currentViewNum == VIEW_CODE_SMS || currentViewNum == VIEW_CODE_CALL) {
                    rectangle.bottom += AndroidUtilities.dp(40);
                }
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                scrollHeight = MeasureSpec.getSize(heightMeasureSpec) - AndroidUtilities.dp(30);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        scrollView.setFillViewport(true);
        container.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        FrameLayout frameLayout = new FrameLayout(context);
        scrollView.addView(frameLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        views[VIEW_PHONE_INPUT] = new PhoneView(context);
        views[VIEW_CODE_MESSAGE] = new LoginActivitySmsView(context, AUTH_TYPE_MESSAGE);
        views[VIEW_CODE_SMS] = new LoginActivitySmsView(context, AUTH_TYPE_SMS);
        views[VIEW_CODE_FLASH_CALL] = new LoginActivitySmsView(context, AUTH_TYPE_FLASH_CALL);
        views[VIEW_CODE_CALL] = new LoginActivitySmsView(context, AUTH_TYPE_CALL);
        views[VIEW_REGISTER] = new LoginActivityRegisterView(context);
        views[VIEW_PASSWORD] = new LoginActivityPasswordView(context);
        views[VIEW_RECOVER] = new LoginActivityRecoverView(context);
        views[VIEW_RESET_WAIT] = new LoginActivityResetWaitView(context);
        views[VIEW_NEW_PASSWORD_STAGE_1] = new LoginActivityNewPasswordView(context, 0);
        views[VIEW_NEW_PASSWORD_STAGE_2] = new LoginActivityNewPasswordView(context, 1);

        for (int a = 0; a < views.length; a++) {
            views[a].setVisibility(a == 0 ? View.VISIBLE : View.GONE);
            frameLayout.addView(views[a], LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT,
                    TelegramDimensions.calculateSize(12),
                    TelegramDimensions.calculateSize(18),
                    TelegramDimensions.calculateSize(12),
                    0)
            );
        }

        Bundle savedInstanceState = loadCurrentState(newAccount, currentAccount);
        if (savedInstanceState != null) {
            currentViewNum = savedInstanceState.getInt("currentViewNum", VIEW_PHONE_INPUT);
            syncContacts = savedInstanceState.getInt("syncContacts", 1) == 1;
            if (currentViewNum >= VIEW_CODE_MESSAGE && currentViewNum <= VIEW_CODE_CALL) {
                int time = savedInstanceState.getInt("open");
                if (time != 0 && Math.abs(System.currentTimeMillis() / 1000 - time) >= 24 * 60 * 60) {
                    currentViewNum = VIEW_PHONE_INPUT;
                    savedInstanceState = null;
                    clearCurrentState();
                }
            } else if (currentViewNum == VIEW_PASSWORD) {
                LoginActivityPasswordView view = (LoginActivityPasswordView) views[VIEW_PASSWORD];
                if (view.currentPassword == null) {
                    currentViewNum = VIEW_PHONE_INPUT;
                    savedInstanceState = null;
                    clearCurrentState();
                }
            } else if (currentViewNum == VIEW_RECOVER) {
                LoginActivityRecoverView view = (LoginActivityRecoverView) views[VIEW_RECOVER];
                if (view.passwordString == null) {
                    currentViewNum = VIEW_PHONE_INPUT;
                    savedInstanceState = null;
                    clearCurrentState();
                }
            }
        }

        floatingButtonContainer = new FrameLayout(context);
        floatingButtonContainer.setVisibility(doneButtonVisible[DONE_TYPE_FLOATING] ? View.VISIBLE : View.GONE);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(100), context.getResources().getColor(R.color.sbdv_green), context.getResources().getColor(R.color.sbdv_green_light));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        floatingButtonContainer.setBackgroundDrawable(drawable);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButtonContainer.setStateListAnimator(animator);
        }
        VerticalPositionAutoAnimator.attach(floatingButtonContainer);
        container.addView(floatingButtonContainer, LayoutHelper.createFrame(TelegramDimensions.selectSize( 40, 56), TelegramDimensions.selectSize( 40, 56), Gravity.RIGHT | Gravity.BOTTOM, 0, 0, TelegramDimensions.selectSize(8, 12), TelegramDimensions.selectSize(8, 12)));
        floatingButtonContainer.setOnClickListener(view -> onDoneButtonPressed());

        floatingButtonIcon = new ImageView(context);
        floatingButtonIcon.setScaleType(ImageView.ScaleType.CENTER);
        floatingButtonIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        floatingButtonIcon.setImageResource(R.drawable.actionbtn_next);
        floatingButtonContainer.setContentDescription(LocaleController.getString("Done", R.string.Done));
        floatingButtonContainer.addView(floatingButtonIcon, LayoutHelper.createFrame(TelegramDimensions.selectSize(20, 30), TelegramDimensions.selectSize(20,30), Gravity.CENTER));

        floatingProgressView = new RadialProgressView(context);
        floatingProgressView.setSize(AndroidUtilities.dp(22));
        floatingProgressView.setProgressColor(Theme.getColor(Theme.key_chats_actionIcon));
        floatingProgressView.setAlpha(0.0f);
        floatingProgressView.setScaleX(0.1f);
        floatingProgressView.setScaleY(0.1f);
        floatingProgressView.setVisibility(View.INVISIBLE);
        floatingButtonContainer.addView(floatingProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (savedInstanceState != null) {
            restoringState = true;
        }
        for (int a = 0; a < views.length; a++) {
            if (savedInstanceState != null) {
                if (a >= VIEW_CODE_MESSAGE && a <= VIEW_CODE_CALL) {
                    if (a == currentViewNum) {
                        views[a].restoreStateParams(savedInstanceState);
                    }
                } else {
                    views[a].restoreStateParams(savedInstanceState);
                }
            }
            if (currentViewNum == a) {
                if (!DeviceUtils.isHuawei()) {
                    actionBar.setBackButtonImage(views[a].needBackButton() || newAccount ? R.drawable.ic_ab_back : 0);
                    View backButtonView = actionBar.getBackButton();
                    if (backButtonView != null) {
                        backButtonView.setNextFocusLeftId(R.id.actionbar_back_btn);
                        backButtonView.setNextFocusUpId(R.id.actionbar_back_btn);
                        backButtonView.setNextFocusRightId(R.id.actionbar_back_btn);
                    }
                }
                views[a].setVisibility(View.VISIBLE);
                views[a].onShow();
                currentDoneType = DONE_TYPE_FLOATING;
                if (a == VIEW_CODE_MESSAGE || a == VIEW_CODE_SMS || a == VIEW_CODE_FLASH_CALL ||
                    a == VIEW_CODE_CALL || a == VIEW_RESET_WAIT) {
                    showDoneButton(false, false);
                } else {
                    showDoneButton(true, false);
                }
                if (a == VIEW_CODE_MESSAGE || a == VIEW_CODE_SMS || a == VIEW_CODE_FLASH_CALL || a == VIEW_CODE_CALL) {
                    currentDoneType = DONE_TYPE_ACTION;
                }
            } else {
                views[a].setVisibility(View.GONE);
            }
        }
        restoringState = false;

        actionBar.setTitle(views[currentViewNum].getHeaderName());
        actionBar.setTitleTextSize(TelegramDimensions.getTitleTextSize());

        LaunchLayout launchLayout = (LaunchLayout) parentLayout.getView().getParent();

        if (SbdvServiceLocator.getConfig().getQrCodeLoginEnabled()) {
            qrLoginView.setVisibility(View.VISIBLE);
            phoneLoginContainer.setVisibility(View.GONE);
            actionBar.setVisibility(View.GONE);

            finalContainer.setPadding(0,0,0,0);
            finalContainer.setFitsSystemWindows(true);

            qrLoginViewController = new QrLoginViewController(
                    qrLoginView,
                    SbdvServiceLocator.getAuthRepository(),
                    Dispatchers.getMain(),
                    (token) -> onAuthSuccess(token.getAuthorization())
            );

            launchLayout.setFullscreenMode(true);
        } else {
            qrLoginView.setVisibility(View.GONE);
            phoneLoginContainer.setVisibility(View.VISIBLE);
            actionBar.setVisibility(View.VISIBLE);

            finalContainer.setPadding(padding,padding,padding,padding);
            finalContainer.setFitsSystemWindows(false);

            launchLayout.setFullscreenMode(false);
        }

        return fragmentView;
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");

        if (newAccount) {
            ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
        }

        if (qrLoginViewController != null) qrLoginViewController.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        if (newAccount) {
            ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        fragmentView.requestLayout();
        try {
            if (currentViewNum >= VIEW_CODE_MESSAGE && currentViewNum <= VIEW_CODE_CALL && views[currentViewNum] instanceof LoginActivitySmsView) {
                int time = ((LoginActivitySmsView) views[currentViewNum]).openTime;
                if (time != 0 && Math.abs(System.currentTimeMillis() / 1000 - time) >= 24 * 60 * 60) {
                    views[currentViewNum].onBackPressed(true);
                    setPage(VIEW_PHONE_INPUT, false, null, true);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (currentViewNum == VIEW_PHONE_INPUT && !needRequestPermissions) {
            SlideView view = views[currentViewNum];
            if (view != null) {
                view.onShow();
            }
        }

        if (qrLoginViewController != null && !qrLoginViewController.getLeftForPhoneLogin()) {
            qrLoginViewController.onResume();
            parentLayout.getView().setBackgroundResource(0);
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (permissions.length == 0 || grantResults.length == 0) {
            return;
        }

        if (requestCode == 6) {
            checkPermissions = false;
            if (currentViewNum == VIEW_PHONE_INPUT) {
                views[currentViewNum].onNextPressed(null);
            }
        } else if (requestCode == 7) {
            checkShowPermissions = false;
            if (currentViewNum == VIEW_PHONE_INPUT) {
                ((PhoneView) views[currentViewNum]).fillNumber();
            }
        }
    }

    public static Bundle loadCurrentState(boolean newAccount, int currentAccount) {
        try {
            Bundle bundle = new Bundle();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2" + (newAccount ? "_" + currentAccount : ""), Context.MODE_PRIVATE);
            Map<String, ?> params = preferences.getAll();
            for (Map.Entry<String, ?> entry : params.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                String[] args = key.split("_\\|_");
                if (args.length == 1) {
                    if (value instanceof String) {
                        bundle.putString(key, (String) value);
                    } else if (value instanceof Integer) {
                        bundle.putInt(key, (Integer) value);
                    } else if (value instanceof Boolean) {
                        bundle.putBoolean(key, (Boolean) value);
                    }
                } else if (args.length == 2) {
                    Bundle inner = bundle.getBundle(args[0]);
                    if (inner == null) {
                        inner = new Bundle();
                        bundle.putBundle(args[0], inner);
                    }
                    if (value instanceof String) {
                        inner.putString(args[1], (String) value);
                    } else if (value instanceof Integer) {
                        inner.putInt(args[1], (Integer) value);
                    } else if (value instanceof Boolean) {
                        inner.putBoolean(args[1], (Boolean) value);
                    }
                }
            }
            return bundle;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private void clearCurrentState() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();
    }

    private void putBundleToEditor(Bundle bundle, SharedPreferences.Editor editor, String prefix) {
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            Object obj = bundle.get(key);
            if (obj instanceof String) {
                if (prefix != null) {
                    editor.putString(prefix + "_|_" + key, (String) obj);
                } else {
                    editor.putString(key, (String) obj);
                }
            } else if (obj instanceof Integer) {
                if (prefix != null) {
                    editor.putInt(prefix + "_|_" + key, (Integer) obj);
                } else {
                    editor.putInt(key, (Integer) obj);
                }
            } else if (obj instanceof Bundle) {
                putBundleToEditor((Bundle) obj, editor, key);
            }
        }
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (dialog == permissionsDialog && !permissionsItems.isEmpty() && getParentActivity() != null) {
                try {
                    getParentActivity().requestPermissions(permissionsItems.toArray(new String[0]), 6);
                } catch (Exception ignore) {

                }
            } else if (dialog == permissionsShowDialog && !permissionsShowItems.isEmpty() && getParentActivity() != null) {
                AndroidUtilities.runOnUIThread(() -> needRequestPermissions = false, 200);
                try {
                    getParentActivity().requestPermissions(permissionsShowItems.toArray(new String[0]), 7);
                } catch (Exception ignore) {

                }
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        Log.d(TAG, "onBackPressed()");

        if (currentViewNum == VIEW_PHONE_INPUT) {
            for (int a = 0; a < views.length; a++) {
                if (views[a] != null) {
                    views[a].onDestroyActivity();
                }
            }
            clearCurrentState();
            return true;
        } else if (currentViewNum == VIEW_PASSWORD) {
            views[currentViewNum].onBackPressed(true);
            setPage(VIEW_PHONE_INPUT, true, null, true);
        } else if (currentViewNum == VIEW_RECOVER || currentViewNum == VIEW_RESET_WAIT) {
            views[currentViewNum].onBackPressed(true);
            setPage(VIEW_PASSWORD, true, null, true);
        } else if (currentViewNum >= VIEW_CODE_MESSAGE && currentViewNum <= VIEW_CODE_CALL) {
            if (views[currentViewNum].onBackPressed(false)) {
                setPage(VIEW_PHONE_INPUT, true, null, true);
            }
        } else if (currentViewNum == VIEW_REGISTER) {
            ((LoginActivityRegisterView) views[currentViewNum]).wrongNumber.callOnClick();
        } else if (currentViewNum == VIEW_NEW_PASSWORD_STAGE_1) {
            views[currentViewNum].onBackPressed(true);
            setPage(VIEW_RECOVER, true, null, true);
        } else if (currentViewNum == VIEW_NEW_PASSWORD_STAGE_2 || currentViewNum == VIEW_CODE_MISSED_CALL) {
            views[currentViewNum].onBackPressed(true);
            setPage(VIEW_NEW_PASSWORD_STAGE_1, true, null, true);
        }
        return false;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        LoginActivityRegisterView registerView = (LoginActivityRegisterView) views[VIEW_REGISTER];
        if (registerView != null) {
            registerView.imageUpdater.onActivityResult(requestCode, resultCode, data);
        }
    }

    //sbdv info
    // алерт - нельзя зарегистрироваться с устройства
    private void needShowCantRegisterAlert() {
        Log.d(TAG, "showCantRegisterAlert");

        Context loginActivityContext = getParentActivity();
        View dialogView = inflate(loginActivityContext, R.layout.sbdv_alert_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(loginActivityContext)
                .setView(dialogView).show();

        alertDialog.setBackgroundColor(Color.TRANSPARENT);
        TextView needAlertTitle = dialogView.findViewById(R.id.alertTitle);
        TextView needAlertMessage = dialogView.findViewById(R.id.alertMessage);
        TextView needAlertPositiveButton = dialogView.findViewById(R.id.positiveButton);
        TextView needAlertNegativeButton = dialogView.findViewById(R.id.negativeButton);

        needAlertTitle.setText(LocaleController.getString("AppName", R.string.AppName));
        needAlertMessage.setText(LocaleController.getString("AppName", R.string.CantRegisterAccountHere));
        needAlertPositiveButton.setText(LocaleController.getString("OK", R.string.OK), null);
        needAlertPositiveButton.setOnClickListener((View v) -> {
            alertDialog.dismiss();
            views[currentViewNum].onBackPressed(true);
            setPage(VIEW_PHONE_INPUT, true, null, true);
        });
        needAlertNegativeButton.setVisibility(View.GONE);
    }

    private void needShowAlert(String title, String text) {
        Log.d(TAG, "needShowAlert(title=" + title + ", text=" + text + ")");
        if (text == null || getParentActivity() == null) {
            Log.d(TAG, "needShowAlert() skipped");
            return;
        }
        Context loginActivityContext = getParentActivity();
        View dialogView = inflate(loginActivityContext, R.layout.sbdv_alert_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(loginActivityContext)
                .setView(dialogView).show();

        alertDialog.setBackgroundColor(Color.TRANSPARENT);
        TextView needAlertTitle = dialogView.findViewById(R.id.alertTitle);
        TextView needAlertMessage = dialogView.findViewById(R.id.alertMessage);
        TextView needAlertPositiveButton = dialogView.findViewById(R.id.positiveButton);
        TextView needAlertNegativeButton = dialogView.findViewById(R.id.negativeButton);

        needAlertTitle.setText(title);
        needAlertMessage.setText(text);
        needAlertPositiveButton.setText(LocaleController.getString("OK", R.string.OK), null);
        needAlertPositiveButton.setOnClickListener((View v) -> alertDialog.dismiss());
        needAlertNegativeButton.setVisibility(View.GONE);
    }

    private void onFieldError(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        AndroidUtilities.shakeViewSpring(view, 3.5f);
    }

    public static void needShowInvalidAlert(BaseFragment fragment, final String phoneNumber, final boolean banned) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }

        View dialogView = inflate(fragment.getParentActivity(), R.layout.sbdv_alert_dialog, null);
        AlertDialog dialog = new AlertDialog.Builder(fragment.getParentActivity())
                .setView(dialogView)
                .show();

        dialog.setBackgroundColor(Color.TRANSPARENT);
        TextView title = dialogView.findViewById(R.id.alertTitle);
        TextView message = dialogView.findViewById(R.id.alertMessage);
        TextView positiveButton = dialogView.findViewById(R.id.positiveButton);
        title.setText(LocaleController.getString("AppName", R.string.AppName));

        if (banned) {
            message.setText(LocaleController.getString("BannedPhoneNumber", R.string.BannedPhoneNumber));
        } else {
            message.setText(LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
        }
        positiveButton.setText(LocaleController.getString("OK", R.string.OK));
        positiveButton.setOnClickListener((View) -> {
            dialog.dismiss();
        });
    }

    private void showDoneButton(boolean show, boolean animated) {
        final boolean floating = currentDoneType == 0;
        if (doneButtonVisible[currentDoneType] == show) {
            return;
        }
        if (showDoneAnimation[currentDoneType] != null) {
            showDoneAnimation[currentDoneType].cancel();
        }
        doneButtonVisible[currentDoneType] = show;
        if (animated) {
            showDoneAnimation[currentDoneType] = new AnimatorSet();
            if (show) {
                if (floating) {
                    floatingButtonContainer.setVisibility(View.VISIBLE);
                    showDoneAnimation[currentDoneType].play(ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Y, 0f));
                } else {
                    doneItem.setVisibility(View.VISIBLE);
                    showDoneAnimation[currentDoneType].playTogether(
                            ObjectAnimator.ofFloat(doneItem, View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(doneItem, View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(doneItem, View.ALPHA, 1.0f));
                }
            } else {
                if (floating) {
                    showDoneAnimation[currentDoneType].play(ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Y, AndroidUtilities.dpf2(70f)));
                } else {
                    showDoneAnimation[currentDoneType].playTogether(
                            ObjectAnimator.ofFloat(doneItem, View.SCALE_X, 0.1f),
                            ObjectAnimator.ofFloat(doneItem, View.SCALE_Y, 0.1f),
                            ObjectAnimator.ofFloat(doneItem, View.ALPHA, 0.0f));
                }
            }
            showDoneAnimation[currentDoneType].addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (showDoneAnimation[floating ? 0 : 1] != null && showDoneAnimation[floating ? 0 : 1].equals(animation)) {
                        if (!show) {
                            if (floating) {
                                floatingButtonContainer.setVisibility(View.GONE);
                            } else {
                                doneItem.setVisibility(View.GONE);
                            }
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (showDoneAnimation[floating ? 0 : 1] != null && showDoneAnimation[floating ? 0 : 1].equals(animation)) {
                        showDoneAnimation[floating ? 0 : 1] = null;
                    }
                }
            });
            final int duration;
            final Interpolator interpolator;
            if (floating) {
                if (show) {
                    duration = 200;
                    interpolator = AndroidUtilities.decelerateInterpolator;
                } else {
                    duration = 150;
                    interpolator = AndroidUtilities.accelerateInterpolator;
                }
            } else {
                duration = 150;
                interpolator = null;
            }
            showDoneAnimation[currentDoneType].setDuration(duration);
            showDoneAnimation[currentDoneType].setInterpolator(interpolator);
            showDoneAnimation[currentDoneType].start();
        } else {
            if (show) {
                if (floating) {
                    floatingButtonContainer.setVisibility(View.VISIBLE);
                    floatingButtonContainer.setTranslationY(0f);
                } else {
                    doneItem.setVisibility(View.VISIBLE);
                    doneItem.setScaleX(1.0f);
                    doneItem.setScaleY(1.0f);
                    doneItem.setAlpha(1.0f);
                }
            } else {
                if (floating) {
                    floatingButtonContainer.setVisibility(View.GONE);
                    floatingButtonContainer.setTranslationY(AndroidUtilities.dpf2(70f));
                } else {
                    doneItem.setVisibility(View.GONE);
                    doneItem.setScaleX(0.1f);
                    doneItem.setScaleY(0.1f);
                    doneItem.setAlpha(0.0f);
                }
            }
        }
    }

    private void onDoneButtonPressed() {
        if (!doneButtonVisible[currentDoneType]) {
            return;
        }
        if (doneProgressView.getTag() != null) {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setMessage(LocaleController.getString("StopLoading", R.string.StopLoading));
            builder.setPositiveButton(LocaleController.getString("WaitMore", R.string.WaitMore), null);
            builder.setNegativeButton(LocaleController.getString("Stop", R.string.Stop), (dialogInterface, i) -> {
                views[currentViewNum].onCancelPressed();
                needHideProgress(true);
            });
            showDialog(builder.create());
        } else {
            views[currentViewNum].onNextPressed(null);
        }
    }

    private void showEditDoneProgress(final boolean show, boolean animated) {
        Log.d(TAG, "showEditDoneProgress(show=" + show + ", animated=" + animated + ")");
        if (doneItemAnimation != null) {
            doneItemAnimation.cancel();
        }
        final boolean floating = currentDoneType == 0;
        if (animated) {
            doneItemAnimation = new AnimatorSet();
            if (show) {
                doneProgressView.setTag(1);
                if (floating) {
                    floatingProgressView.setVisibility(View.VISIBLE);
                    floatingButtonContainer.setEnabled(false);
                    doneItemAnimation.playTogether(
                            ObjectAnimator.ofFloat(floatingButtonIcon, View.SCALE_X, 0.1f),
                            ObjectAnimator.ofFloat(floatingButtonIcon, View.SCALE_Y, 0.1f),
                            ObjectAnimator.ofFloat(floatingButtonIcon, View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(floatingProgressView, View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(floatingProgressView, View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(floatingProgressView, View.ALPHA, 1.0f));
                } else {
                    doneProgressView.setVisibility(View.VISIBLE);
                    doneItemAnimation.playTogether(
                            ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_X, 0.1f),
                            ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_Y, 0.1f),
                            ObjectAnimator.ofFloat(doneItem.getContentView(), View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(doneProgressView, View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(doneProgressView, View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(doneProgressView, View.ALPHA, 1.0f));
                }
            } else {
                doneProgressView.setTag(null);
                if (floating) {
                    floatingButtonIcon.setVisibility(View.VISIBLE);
                    floatingButtonContainer.setEnabled(true);
                    doneItemAnimation.playTogether(
                            ObjectAnimator.ofFloat(floatingProgressView, View.SCALE_X, 0.1f),
                            ObjectAnimator.ofFloat(floatingProgressView, View.SCALE_Y, 0.1f),
                            ObjectAnimator.ofFloat(floatingProgressView, View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(floatingButtonIcon, View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(floatingButtonIcon, View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(floatingButtonIcon, View.ALPHA, 1.0f));
                } else {
                    doneItem.getContentView().setVisibility(View.VISIBLE);
                    doneItemAnimation.playTogether(
                            ObjectAnimator.ofFloat(doneProgressView, View.SCALE_X, 0.1f),
                            ObjectAnimator.ofFloat(doneProgressView, View.SCALE_Y, 0.1f),
                            ObjectAnimator.ofFloat(doneProgressView, View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(doneItem.getContentView(), View.ALPHA, 1.0f));
                }
            }
            doneItemAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        if (floating) {
                            if (!show) {
                                floatingProgressView.setVisibility(View.INVISIBLE);
                            } else {
                                floatingButtonIcon.setVisibility(View.INVISIBLE);
                            }
                        } else {
                            if (!show) {
                                doneProgressView.setVisibility(View.INVISIBLE);
                            } else {
                                doneItem.getContentView().setVisibility(View.INVISIBLE);
                            }
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        doneItemAnimation = null;
                    }
                }
            });
            doneItemAnimation.setDuration(150);
            doneItemAnimation.start();
        } else {
            if (show) {
                if (floating) {
                    floatingProgressView.setVisibility(View.VISIBLE);
                    floatingButtonIcon.setVisibility(View.INVISIBLE);
                    floatingButtonContainer.setEnabled(false);
                    floatingButtonIcon.setScaleX(0.1f);
                    floatingButtonIcon.setScaleY(0.1f);
                    floatingButtonIcon.setAlpha(0.0f);
                    floatingProgressView.setScaleX(1.0f);
                    floatingProgressView.setScaleY(1.0f);
                    floatingProgressView.setAlpha(1.0f);
                } else {
                    doneProgressView.setVisibility(View.VISIBLE);
                    doneItem.getContentView().setVisibility(View.INVISIBLE);
                    doneItem.getContentView().setScaleX(0.1f);
                    doneItem.getContentView().setScaleY(0.1f);
                    doneItem.getContentView().setAlpha(0.0f);
                    doneProgressView.setScaleX(1.0f);
                    doneProgressView.setScaleY(1.0f);
                    doneProgressView.setAlpha(1.0f);
                }
            } else {
                doneProgressView.setTag(null);
                if (floating) {
                    floatingProgressView.setVisibility(View.INVISIBLE);
                    floatingButtonIcon.setVisibility(View.VISIBLE);
                    floatingButtonContainer.setEnabled(true);
                    floatingProgressView.setScaleX(0.1f);
                    floatingProgressView.setScaleY(0.1f);
                    floatingProgressView.setAlpha(0.0f);
                    floatingButtonIcon.setScaleX(1.0f);
                    floatingButtonIcon.setScaleY(1.0f);
                    floatingButtonIcon.setAlpha(1.0f);
                } else {
                    doneItem.getContentView().setVisibility(View.VISIBLE);
                    doneProgressView.setVisibility(View.INVISIBLE);
                    doneProgressView.setScaleX(0.1f);
                    doneProgressView.setScaleY(0.1f);
                    doneProgressView.setAlpha(0.0f);
                    doneItem.getContentView().setScaleX(1.0f);
                    doneItem.getContentView().setScaleY(1.0f);
                    doneItem.getContentView().setAlpha(1.0f);
                }
            }
        }
    }

    private void needShowProgress(final int reqiestId) {
        needShowProgress(reqiestId, true);
    }

    private void needShowProgress(final int reqiestId, boolean animated) {
        progressRequestId = reqiestId;
        showEditDoneProgress(true, animated);
    }

    private void needHideProgress(boolean cancel) {
        needHideProgress(cancel, true);
    }

    private void needHideProgress(boolean cancel, boolean animated) {
        Log.d(TAG, "needHideProgress(cancel=" + cancel + ", animated=" + animated + ")");
        if (progressRequestId != 0) {
            if (cancel) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(progressRequestId, true);
            }
            progressRequestId = 0;
        }
        showEditDoneProgress(false, animated);
    }

    public void setPage(int page, boolean animated, Bundle params, boolean back) {
        final boolean needFloatingButton = page == VIEW_PHONE_INPUT || page == VIEW_REGISTER || page == VIEW_PASSWORD || page == VIEW_RECOVER ||
                page == VIEW_NEW_PASSWORD_STAGE_1 || page == VIEW_NEW_PASSWORD_STAGE_2 || page == VIEW_CODE_MISSED_CALL;
        if (needFloatingButton) {
            if (page == VIEW_PHONE_INPUT) {
                checkPermissions = true;
                checkShowPermissions = true;
            }
            currentDoneType = DONE_TYPE_ACTION;
            showDoneButton(false, animated);
            currentDoneType = DONE_TYPE_FLOATING;
            showEditDoneProgress(false, false);
            if (!animated) {
                showDoneButton(true, false);
            }
        } else {
            currentDoneType = DONE_TYPE_FLOATING;
            showDoneButton(false, animated);
            if (page != VIEW_RESET_WAIT) {
                currentDoneType = DONE_TYPE_ACTION;
            }
        }
        if (animated) {
            final SlideView outView = views[currentViewNum];
            final SlideView newView = views[page];
            currentViewNum = page;
            if (!DeviceUtils.isHuawei()) {
                actionBar.setBackButtonImage(newView.needBackButton() || newAccount ? R.drawable.ic_ab_back : 0);
                View backButtonView = actionBar.getBackButton();
                if (backButtonView != null) {
                    backButtonView.setNextFocusLeftId(R.id.actionbar_back_btn);
                    backButtonView.setNextFocusUpId(R.id.actionbar_back_btn);
                    backButtonView.setNextFocusRightId(R.id.actionbar_back_btn);
                }
            }
            newView.setParams(params, false);
            actionBar.setTitle(newView.getHeaderName());
            setParentActivityTitle(newView.getHeaderName());
            newView.onShow();
            newView.setX(back ? -AndroidUtilities.displaySize.x : AndroidUtilities.displaySize.x);
            newView.setVisibility(View.VISIBLE);

            AnimatorSet pagesAnimation = new AnimatorSet();
            pagesAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (currentDoneType == DONE_TYPE_FLOATING && needFloatingButton) {
                        showDoneButton(true, true);
                    }
                    outView.setVisibility(View.GONE);
                    outView.setX(0);
                }
            });
            pagesAnimation.playTogether(
                    ObjectAnimator.ofFloat(outView, View.TRANSLATION_X, back ? AndroidUtilities.displaySize.x : -AndroidUtilities.displaySize.x),
                    ObjectAnimator.ofFloat(newView, View.TRANSLATION_X, 0));
            pagesAnimation.setDuration(300);
            pagesAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
            pagesAnimation.start();
        } else {
            if (!DeviceUtils.isHuawei()) {
                actionBar.setBackButtonImage(views[page].needBackButton() || newAccount ? R.drawable.ic_ab_back : 0);
                View backButtonView = actionBar.getBackButton();
                if (backButtonView != null) {
                    backButtonView.setNextFocusLeftId(R.id.actionbar_back_btn);
                    backButtonView.setNextFocusUpId(R.id.actionbar_back_btn);
                    backButtonView.setNextFocusRightId(R.id.actionbar_back_btn);
                }
            }
            views[currentViewNum].setVisibility(View.GONE);
            currentViewNum = page;
            views[page].setParams(params, false);
            views[page].setVisibility(View.VISIBLE);
            actionBar.setTitle(views[page].getHeaderName());
            setParentActivityTitle(views[page].getHeaderName());
            views[page].onShow();
        }
    }

    @Override
    public void saveSelfArgs(Bundle outState) {
        try {
            Bundle bundle = new Bundle();
            bundle.putInt("currentViewNum", currentViewNum);
            bundle.putInt("syncContacts", syncContacts ? 1 : 0);
            for (int a = 0; a <= currentViewNum; a++) {
                SlideView v = views[a];
                if (v != null) {
                    v.saveStateParams(bundle);
                }
            }
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.clear();
            putBundleToEditor(bundle, editor, null);
            editor.commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void needFinishActivity(boolean afterSignup) {
        Log.d(TAG, "needFinishActivity(). Parent activity: " + getParentActivity());
        clearCurrentState();
        Activity parentActivity = getParentActivity();
        if (parentActivity instanceof LaunchActivity) {
            Config config = SbdvServiceLocator.getConfig();
            Log.d(TAG, "needFinishActivity(). New account: " + newAccount);
            if (newAccount) {
                newAccount = false;
                ((LaunchActivity) getParentActivity()).switchToAccount(currentAccount, false);
                finishFragment();
            } else {
                if (!Config.IS_SBERDEVICE) {
                    final Bundle args = new Bundle();
                    args.putBoolean("afterSignup", afterSignup);
                    presentFragment(new DialogsActivity(args), true);
                } else {
                    if (!config.getIntegratedWithSingleCallsPlace()) {
                        parentLayout.presentFragmentInternalRemoveOld(true, this);
                        if (parentActivity.getCurrentFocus() != null) {
                            AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                        }
                    }
                }
                if (config.getIntegratedWithSingleCallsPlace()) {
                    AuthorizationFeature authorizationFeature = SbdvServiceLocator.getAuthorizationFeatureSharedInstance();
                    authorizationFeature.setAuthorizationCallback(
                            () -> {
                                Log.d(TAG, "authorization state updated, finish launch activity: " + this);
                                AndroidUtilities.runOnUIThread(() -> {
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
                                });
                            });
                } else {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
                }
            }
        }
    }

    private void onAuthSuccess(TLRPC.TL_auth_authorization res) {
        onAuthSuccess(res, false);
    }

    private void onAuthSuccess(TLRPC.TL_auth_authorization res, boolean afterSignup) {
        Log.d("LoginActivity", "onAuthSuccess()");
        ConnectionsManager.getInstance(currentAccount).setUserId(res.user.id);
        UserConfig.getInstance(currentAccount).clearConfig();
        MessagesController.getInstance(currentAccount).cleanup(); // cleanup all contacts
        UserConfig.getInstance(currentAccount).syncContacts = syncContacts;
        UserConfig.getInstance(currentAccount).setCurrentUser(res.user);
        UserConfig.getInstance(currentAccount).saveConfig(true);
        MessagesStorage.getInstance(currentAccount).cleanup(true);
        ArrayList<TLRPC.User> users = new ArrayList<>();
        users.add(res.user);
        MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, true, true);
        MessagesController.getInstance(currentAccount).putUser(res.user, false);
        ContactsController.getInstance(currentAccount).checkAppAccount(); // load contacts for current account
        if (!SbdvServiceLocator.getConfig().getPrudentNetworking()) MessagesController.getInstance(currentAccount).checkPromoInfo(true);
        ConnectionsManager.getInstance(currentAccount).updateDcSettings();
        analyticsCollector.onAppEvent(AppEvent.SUCCESS_LOGIN);
        needFinishActivity(afterSignup);
    }

    private void fillNextCodeParams(Bundle params, TLRPC.TL_auth_sentCode res) {
        params.putString("phoneHash", res.phone_code_hash);
        if (res.next_type instanceof TLRPC.TL_auth_codeTypeCall) {
            params.putInt("nextType", 4);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeFlashCall) {
            params.putInt("nextType", 3);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeSms) {
            params.putInt("nextType", 2);
        }
        if (res.type instanceof TLRPC.TL_auth_sentCodeTypeApp) {
            params.putInt("type", VIEW_CODE_MESSAGE);
            params.putInt("length", res.type.length);
            setPage(VIEW_CODE_MESSAGE, true, params, false);
        } else {
            if (res.timeout == 0) {
                res.timeout = 60;
            }
            params.putInt("timeout", res.timeout * 1000);
            if (res.type instanceof TLRPC.TL_auth_sentCodeTypeCall) {
                params.putInt("type", VIEW_CODE_CALL);
                params.putInt("length", res.type.length);
                setPage(VIEW_CODE_CALL, true, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeFlashCall) {
                params.putInt("type", VIEW_CODE_FLASH_CALL);
                params.putString("pattern", res.type.pattern);
                setPage(VIEW_CODE_FLASH_CALL, true, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeSms) {
                params.putInt("type", VIEW_CODE_SMS);
                params.putInt("length", res.type.length);
                setPage(VIEW_CODE_SMS, true, params, false);
            }
        }
    }

    private TLRPC.TL_help_termsOfService currentTermsOfService;

    public class PhoneView extends SlideView implements AdapterView.OnItemSelectedListener {

        //sbdv info
        //Тут выбираем страну, вводим код страны и номер телефона
        private int sbdvCountryButtonBottomMargin = TelegramDimensions.selectSize(12, 18);

        private EditTextBoldCursor codeField;
        private HintEditText phoneField;
        private TextView countryButton; //кнопка выберите страну
        private View view;
        private TextView textView; //код страны
        private TextView textView2; //номер телефона
        private CheckBoxCell checkBoxCell;
        private CheckBoxCell testBackendCheckBox;

        @CountryState
        private int countryState = COUNTRY_STATE_NOT_SET_OR_VALID;
        private CountrySelectActivity.Country currentCountry;

        private ArrayList<CountrySelectActivity.Country> countriesArray = new ArrayList<>();
        private HashMap<String, List<CountrySelectActivity.Country>> codesMap = new HashMap<>();
        private HashMap<String, List<String>> phoneFormatMap = new HashMap<>();

        private boolean ignoreSelection = false;
        private boolean ignoreOnTextChange = false;
        private boolean ignoreOnPhoneChange = false;
        private boolean ignoreOnPhoneChangePaste = false;
        private boolean nextPressed = false;

        public PhoneView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            countryButton = new TextView(context);
            countryButton.setId(R.id.country_button_id);
            countryButton.setNextFocusUpId(R.id.country_button_id);
            countryButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getPhoneLoginEditTextButtonTextSize());
            countryButton.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            countryButton.setTextColor(context.getColor(R.color.white));
            countryButton.setMaxLines(1);
            countryButton.setSingleLine(true);
            countryButton.setEllipsize(TextUtils.TruncateAt.END);
            countryButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
            countryButton.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            addView(countryButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 0, 0, 0));
            countryButton.setOnClickListener(view -> {
                CountrySelectActivity fragment = new CountrySelectActivity(true, countriesArray);
                fragment.setCountrySelectActivityDelegate((country) -> {
                    selectCountry(country);
                    AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(phoneField), 300);
                    phoneField.requestFocus();
                    phoneField.setSelection(phoneField.length());
                });
                presentFragment(fragment);
            });

            view = new View(context);
            view.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            view.setBackgroundColor(context.getColor(R.color.white));

            LinearLayout linearLayout = new LinearLayout(context); // контейнер для кода страны + номера телефона
            linearLayout.setOrientation(HORIZONTAL);
            addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, sbdvCountryButtonBottomMargin, 0, 0));

            textView = new TextView(context); //country code
            textView.setText("+");
            textView.setTextColor(context.getColor(R.color.white));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getPhoneLoginEditTextButtonTextSize());
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            codeField = new EditTextBoldCursor(context); //phone number
            codeField.setInputType(InputType.TYPE_CLASS_PHONE);
            codeField.setTextColor(context.getColor(R.color.white));
            codeField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            codeField.setCursorColor(context.getColor(R.color.white));
            codeField.setCursorSize(AndroidUtilities.dp(TelegramDimensions.getPhoneLoginEditTextButtonTextSize()));
            codeField.setCursorWidth(1.5f);
            codeField.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getPhoneLoginEditTextButtonTextSize());
            codeField.setMaxLines(1);
            codeField.setMovementMethod(null);
            codeField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            InputFilter[] inputFilters = new InputFilter[1];
            inputFilters[0] = new InputFilter.LengthFilter(5);
            codeField.setFilters(inputFilters);
            linearLayout.addView(codeField, LayoutHelper.createLinear(68, 36, -9, 0, 16, 0));
            codeField.setOnKeyListener(new OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                        AndroidUtilities.showKeyboard(v);
                        return true;
                    }
                    return false;
                }
            });

            codeField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void afterTextChanged(Editable editable) {
                    if (ignoreOnTextChange) {
                        return;
                    }
                    ignoreOnTextChange = true;
                    String text = PhoneFormat.stripExceptNumbers(codeField.getText().toString());
                    codeField.setText(text);
                    if (text.length() == 0) {
                        countryButton.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                        phoneField.setHintText(null);
                        countryState = COUNTRY_STATE_EMPTY;
                    } else {
                        CountrySelectActivity.Country country;
                        boolean ok = false;
                        String textToSet = null;
                        if (text.length() > 4) {
                            for (int a = 4; a >= 1; a--) {
                                String sub = text.substring(0, a);

                                List<CountrySelectActivity.Country> list = codesMap.get(sub);
                                if (list == null) {
                                    country = null;
                                } else if (list.size() > 1) {
                                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                                    String lastMatched = preferences.getString("phone_code_last_matched_" + sub, null);

                                    country = list.get(list.size() - 1);
                                    if (lastMatched != null) {
                                        for (CountrySelectActivity.Country c : countriesArray) {
                                            if (Objects.equals(c.shortname, lastMatched)) {
                                                country = c;
                                                break;
                                            }
                                        }
                                    }
                                } else {
                                    country = list.get(0);
                                }

                                if (country != null) {
                                    ok = true;
                                    textToSet = text.substring(a) + phoneField.getText().toString();
                                    codeField.setText(text = sub);
                                    break;
                                }
                            }
                            if (!ok) {
                                textToSet = text.substring(1) + phoneField.getText().toString();
                                codeField.setText(text = text.substring(0, 1));
                            }
                        }

                        CountrySelectActivity.Country lastMatchedCountry = null;
                        int matchedCountries = 0;
                        for (CountrySelectActivity.Country c : countriesArray) {
                            if (c.code.startsWith(text)) {
                                matchedCountries++;
                                if (c.code.equals(text)) {
                                    if (lastMatchedCountry != null && lastMatchedCountry.code.equals(c.code)) {
                                        matchedCountries--;
                                    }
                                    lastMatchedCountry = c;
                                }
                            }
                        }
                        if (matchedCountries == 1 && lastMatchedCountry != null && textToSet == null) {
                            textToSet = text.substring(lastMatchedCountry.code.length()) + phoneField.getText().toString();
                            codeField.setText(text = lastMatchedCountry.code);
                        }

                        List<CountrySelectActivity.Country> list = codesMap.get(text);
                        if (list == null) {
                            country = null;
                        } else if (list.size() > 1) {
                            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                            String lastMatched = preferences.getString("phone_code_last_matched_" + text, null);

                            country = list.get(list.size() - 1);
                            if (lastMatched != null) {
                                for (CountrySelectActivity.Country c : countriesArray) {
                                    if (Objects.equals(c.shortname, lastMatched)) {
                                        country = c;
                                        break;
                                    }
                                }
                            }
                        } else {
                            country = list.get(0);
                        }
                        if (country != null) {
                            int index = countriesArray.indexOf(country);
                            if (index != -1) {
                                ignoreSelection = true;
                                currentCountry = country;
                                setCountryHint(text, country);
                                countryState = COUNTRY_STATE_NOT_SET_OR_VALID;
                            } else {
                                countryButton.setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
                                phoneField.setHintText(null);
                                countryState = COUNTRY_STATE_INVALID;
                            }
                        } else {
                            countryButton.setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
                            phoneField.setHintText(null);
                            countryState = COUNTRY_STATE_INVALID;
                        }
                        if (!ok) {
                            codeField.setSelection(codeField.getText().length());
                        }
                        if (textToSet != null) {
                            phoneField.requestFocus();
                            phoneField.setText(textToSet);
                            phoneField.setSelection(phoneField.length());
                        }
                    }
                    ignoreOnTextChange = false;
                }
            });
            codeField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    phoneField.requestFocus();
                    phoneField.setSelection(phoneField.length());
                    return true;
                }
                return false;
            });

            phoneField = new HintEditText(context) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        if (!AndroidUtilities.showKeyboard(this)) {
                            clearFocus();
                            requestFocus();
                        }
                    }
                    return super.onTouchEvent(event);
                }
            };
            phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
            phoneField.setTextColor(context.getColor(R.color.white));
            phoneField.setHintTextColor(context.getColor(R.color.white));
            phoneField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            phoneField.setPadding(0, 0, 0, 0);
            phoneField.setCursorColor(context.getColor(R.color.white));
            phoneField.setCursorSize(AndroidUtilities.dp(20));
            phoneField.setCursorWidth(1.5f);
            phoneField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getPhoneLoginEditTextButtonTextSize());
            phoneField.setMaxLines(1);
            phoneField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            phoneField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            phoneField.setMovementMethod(null);
            linearLayout.addView(phoneField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));
            phoneField.addTextChangedListener(new TextWatcher() {

                private int characterAction = -1;
                private int actionPosition;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (count == 0 && after == 1) {
                        characterAction = 1;
                    } else if (count == 1 && after == 0) {
                        if (s.charAt(start) == ' ' && start > 0) {
                            characterAction = 3;
                            actionPosition = start - 1;
                        } else {
                            characterAction = 2;
                        }
                    } else {
                        characterAction = -1;
                    }
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!ENABLE_PASTED_TEXT_PROCESSING || ignoreOnPhoneChange || ignoreOnPhoneChangePaste) {
                        return;
                    }

                    String str = s.toString().substring(start, start + count).replaceAll("[^\\d]+", "");
                    if (str.isEmpty()) {
                        return;
                    }

                    ignoreOnPhoneChangePaste = true;
                    for (int i = Math.min(3, str.length()); i >= 0; i--) {
                        String code = str.substring(0, i);

                        List<CountrySelectActivity.Country> list = codesMap.get(code);
                        if (list != null && !list.isEmpty()) {
                            List<String> patterns = phoneFormatMap.get(code);

                            if (patterns == null || patterns.isEmpty()) {
                                continue;
                            }

                            for (String pattern : patterns) {
                                String pat = pattern.replace(" ", "");
                                if (pat.length() == str.length() - i) {
                                    codeField.setText(code);
                                    ignoreOnTextChange = true;
                                    phoneField.setText(str.substring(i));
                                    ignoreOnTextChange = false;

                                    afterTextChanged(phoneField.getText());
                                    phoneField.setSelection(phoneField.getText().length(), phoneField.getText().length());
                                    break;
                                }
                            }
                        }
                    }
                    ignoreOnPhoneChangePaste = false;
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (ignoreOnPhoneChange) {
                        return;
                    }
                    int start = phoneField.getSelectionStart();
                    String phoneChars = "0123456789";
                    String str = phoneField.getText().toString();
                    if (characterAction == 3) {
                        str = str.substring(0, actionPosition) + str.substring(actionPosition + 1);
                        start--;
                    }
                    StringBuilder builder = new StringBuilder(str.length());
                    for (int a = 0; a < str.length(); a++) {
                        String ch = str.substring(a, a + 1);
                        if (phoneChars.contains(ch)) {
                            builder.append(ch);
                        }
                    }
                    ignoreOnPhoneChange = true;
                    String hint = phoneField.getHintText();
                    if (hint != null) {
                        for (int a = 0; a < builder.length(); a++) {
                            if (a < hint.length()) {
                                if (hint.charAt(a) == ' ') {
                                    builder.insert(a, ' ');
                                    a++;
                                    if (start == a && characterAction != 2 && characterAction != 3) {
                                        start++;
                                    }
                                }
                            } else {
                                builder.insert(a, ' ');
                                if (start == a + 1 && characterAction != 2 && characterAction != 3) {
                                    start++;
                                }
                                break;
                            }
                        }
                    }
                    int destLength = builder.length();
                    if (hint != null) {
                        destLength = Math.min(destLength, hint.length());
                    }
                    s.replace(0, s.length(), builder,0, destLength);
                    if (start >= 0) {
                        phoneField.setSelection(Math.min(start, phoneField.length()));
                    }
                    phoneField.onTextChange();
                    ignoreOnPhoneChange = false;
                }
            });
            phoneField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });
            phoneField.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && phoneField.length() == 0) {
                    codeField.requestFocus();
                    codeField.setSelection(codeField.length());
                    codeField.dispatchKeyEvent(event);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                    AndroidUtilities.showKeyboard(v);
                    return true;
                }
                return false;
            });

            textView2 = new TextView(context);
            textView2.setText(LocaleController.getString("StartText", R.string.StartText));
            textView2.setTextColor(0x90ffffff);
            textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getSmallTextSize());
            textView2.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView2.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(textView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, TelegramDimensions.calculateSize(18), 0, 10));

            if (newAccount) {
                checkBoxCell = new CheckBoxCell(context, 2);
                checkBoxCell.setText(LocaleController.getString("SyncContacts", R.string.SyncContacts), "", syncContacts, false);
                addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
                checkBoxCell.setOnClickListener(new OnClickListener() {

                    private Toast visibleToast;

                    @Override
                    public void onClick(View v) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        CheckBoxCell cell = (CheckBoxCell) v;
                        syncContacts = !syncContacts;
                        cell.setChecked(syncContacts, true);
                        try {
                            if (visibleToast != null) {
                                visibleToast.cancel();
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (syncContacts) {
                            BulletinFactory.of((FrameLayout) fragmentView, null).createSimpleBulletin(R.raw.contacts_sync_on, LocaleController.getString("SyncContactsOn", R.string.SyncContactsOn)).show();
                        } else {
                            BulletinFactory.of((FrameLayout) fragmentView, null).createSimpleBulletin(R.raw.contacts_sync_off, LocaleController.getString("SyncContactsOff", R.string.SyncContactsOff)).show();
                        }
                    }
                });
            }

            if (BuildVars.DEBUG_PRIVATE_VERSION) {
                testBackendCheckBox = new CheckBoxCell(context, 2);
                testBackendCheckBox.setText("Test Backend", "", testBackend, false);
                addView(testBackendCheckBox, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
                testBackendCheckBox.setOnClickListener(v -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    CheckBoxCell cell = (CheckBoxCell) v;
                    testBackend = !testBackend;
                    cell.setChecked(testBackend, true);
                });
            }

            HashMap<String, String> languageMap = new HashMap<>();

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(getResources().getAssets().open("countries.txt")));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] args = line.split(";");
                    CountrySelectActivity.Country countryWithCode = new CountrySelectActivity.Country();
                    countryWithCode.name = args[2];
                    countryWithCode.code = args[0];
                    countryWithCode.shortname = args[1];
                    countriesArray.add(0, countryWithCode);

                    List<CountrySelectActivity.Country> countryList = codesMap.get(args[0]);
                    if (countryList == null) {
                        codesMap.put(args[0], countryList = new ArrayList<>());
                    }
                    countryList.add(countryWithCode);

                    if (args.length > 3) {
                        phoneFormatMap.put(args[0], Collections.singletonList(args[3]));
                    }
                    languageMap.put(args[1], args[2]);
                }
                reader.close();
            } catch (Exception e) {
                FileLog.e(e);
            }

            Collections.sort(countriesArray, Comparator.comparing(o -> o.name));

            String country = null;

            try {
                TelephonyManager telephonyManager = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyManager != null) {
                    country = null;//telephonyManager.getSimCountryIso().toUpperCase();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (country != null) {
                setCountry(languageMap, country.toUpperCase());
            } else {
                TLRPC.TL_help_getNearestDc req = new TLRPC.TL_help_getNearestDc();
                getAccountInstance().getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response == null) {
                        return;
                    }
                    TLRPC.TL_nearestDc res = (TLRPC.TL_nearestDc) response;
                    Log.d(TAG, "account instance country: [" + res.country + "]");
                    if (codeField.length() == 0) {
                        setCountry(languageMap, res.country.toUpperCase());
                    }
                }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
            }
            if (codeField.length() == 0) {
                countryButton.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                phoneField.setHintText(null);
                countryState = 1;
            }

            if (codeField.length() != 0) {
                phoneField.requestFocus();
                phoneField.setSelection(phoneField.length());
            } else {
                codeField.requestFocus();
            }
            loadCountries();
        }

        private void loadCountries() {
            TLRPC.TL_help_getCountriesList req = new TLRPC.TL_help_getCountriesList();
            req.lang_code = LocaleController.getInstance().getCurrentLocaleInfo() != null ? LocaleController.getInstance().getCurrentLocaleInfo().getLangCode() : Locale.getDefault().getCountry();
            getConnectionsManager().sendRequest(req, (response, error) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    if (error == null) {
                        countriesArray.clear();
                        codesMap.clear();
                        phoneFormatMap.clear();

                        TLRPC.TL_help_countriesList help_countriesList = (TLRPC.TL_help_countriesList) response;
                        for (int i = 0; i < help_countriesList.countries.size(); i++) {
                            TLRPC.TL_help_country c = help_countriesList.countries.get(i);
                            for (int k = 0; k < c.country_codes.size(); k++) {
                                TLRPC.TL_help_countryCode countryCode = c.country_codes.get(k);
                                if (countryCode != null) {
                                    CountrySelectActivity.Country countryWithCode = new CountrySelectActivity.Country();
                                    countryWithCode.name = c.name;
                                    countryWithCode.defaultName = c.default_name;
                                    if (countryWithCode.name == null && countryWithCode.defaultName != null) {
                                        countryWithCode.name = countryWithCode.defaultName;
                                    }
                                    countryWithCode.code = countryCode.country_code;
                                    countryWithCode.shortname = c.iso2;

                                    countriesArray.add(countryWithCode);
                                    List<CountrySelectActivity.Country> countryList = codesMap.get(countryCode.country_code);
                                    if (countryList == null) {
                                        codesMap.put(countryCode.country_code, countryList = new ArrayList<>());
                                    }
                                    countryList.add(countryWithCode);
                                    if (countryCode.patterns.size() > 0) {
                                        phoneFormatMap.put(countryCode.country_code, countryCode.patterns);
                                    }
                                }
                            }
                        }
                        codeField.setText(codeField.getText());
                    }
                });
            }, ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        public void selectCountry(CountrySelectActivity.Country country) {
            ignoreOnTextChange = true;
            String code = country.code;
            codeField.setText(code);
            setCountryHint(code, country);
            currentCountry = country;
            countryState = COUNTRY_STATE_NOT_SET_OR_VALID;
            ignoreOnTextChange = false;

            MessagesController.getGlobalMainSettings().edit().putString("phone_code_last_matched_" + country.code, country.shortname).apply();
        }

        private String countryCodeForHint;
        private void setCountryHint(String code, CountrySelectActivity.Country country) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            String flag = LocaleController.getLanguageFlag(country.shortname);
            if (flag != null) {
                sb.append(flag).append(" ");
                sb.setSpan(new ReplacementSpan() {
                    @Override
                    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                        return AndroidUtilities.dp(16);
                    }

                    @Override
                    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {}
                }, flag.length(), flag.length() + 1, 0);
            }
            sb.append(country.name);
            countryButton.setText(sb);
            countryCodeForHint = code;
            wasCountryHintIndex = -1;
            invalidateCountryHint();
        }

        private int wasCountryHintIndex = -1;
        private void invalidateCountryHint() {
            String code = countryCodeForHint;
            String str = phoneField.getText() != null ? phoneField.getText().toString().replace(" ", "") : "";

            if (phoneFormatMap.get(code) != null && !phoneFormatMap.get(code).isEmpty()) {
                int index = -1;
                List<String> patterns = phoneFormatMap.get(code);
                if (!str.isEmpty()) {
                    for (int i = 0; i < patterns.size(); i++) {
                        String pattern = patterns.get(i);
                        if (str.startsWith(pattern.replace(" ", "").replace("X", "").replace("0", ""))) {
                            index = i;
                            break;
                        }
                    }
                }
                if (index == -1) {
                    for (int i = 0; i < patterns.size(); i++) {
                        String pattern = patterns.get(i);
                        if (pattern.startsWith("X") || pattern.startsWith("0")) {
                            index = i;
                            break;
                        }
                    }
                    if (index == -1) {
                        index = 0;
                    }
                }

                if (wasCountryHintIndex != index) {
                    String hint = phoneFormatMap.get(code).get(index);
                    int ss = phoneField.getSelectionStart(), se = phoneField.getSelectionEnd();
                    phoneField.setHintText(hint != null ? hint.replace('X', '0') : null);
                    phoneField.setSelection(
                            Math.max(0, Math.min(phoneField.length(), ss)),
                            Math.max(0, Math.min(phoneField.length(), se))
                    );
                    wasCountryHintIndex = index;
                }
            } else if (wasCountryHintIndex != -1) {
                int ss = phoneField.getSelectionStart(), se = phoneField.getSelectionEnd();
                phoneField.setHintText(null);
                phoneField.setSelection(ss, se);
                wasCountryHintIndex = -1;
            }
        }

        private void setCountry(HashMap<String, String> languageMap, String country) {
            String name = languageMap.get(country);
            if (name != null && countriesArray != null) {
                CountrySelectActivity.Country countryWithCode = null;
                for (int i = 0; i < countriesArray.size(); i++) {
                    if (countriesArray.get(i) != null && countriesArray.get(i).shortname.equals(country)) {
                        countryWithCode = countriesArray.get(i);
                        break;
                    }
                }
                if (countryWithCode != null) {
                    codeField.setText(countryWithCode.code);
                    countryState = COUNTRY_STATE_NOT_SET_OR_VALID;
                }
            }
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            if (ignoreSelection) {
                ignoreSelection = false;
                return;
            }
            ignoreOnTextChange = true;
            CountrySelectActivity.Country countryWithCode = countriesArray.get(i);
            codeField.setText(countryWithCode.code);
            ignoreOnTextChange = false;
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }

        @Override
        public void onNextPressed(String code) {
            Log.d(TAG, "onNextPressed()");
            if (getParentActivity() == null || nextPressed) {
                Log.d(TAG, "onNextPressed: skip. Parent activity: " + getParentActivity() + ", next pressed: " + nextPressed);
                return;
            }
            TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("sim status = " + tm.getSimState());
            }
            int state = tm.getSimState();
            boolean simcardAvailable = state != TelephonyManager.SIM_STATE_ABSENT && state != TelephonyManager.SIM_STATE_UNKNOWN && tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE && !AndroidUtilities.isAirplaneModeOn();
            boolean allowCall = true;
            boolean allowCancelCall = true;
            boolean allowReadCallLog = true;
            if (Build.VERSION.SDK_INT >= 23 && simcardAvailable) {
                allowCall = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
                allowCancelCall = getParentActivity().checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
                allowReadCallLog = Build.VERSION.SDK_INT < 28 || getParentActivity().checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
                if (checkPermissions) {
                    permissionsItems.clear();
                    if (!allowCall) {
                        permissionsItems.add(Manifest.permission.READ_PHONE_STATE);
                    }
                    if (!allowCancelCall) {
                        permissionsItems.add(Manifest.permission.CALL_PHONE);
                    }
                    if (!allowReadCallLog) {
                        permissionsItems.add(Manifest.permission.READ_CALL_LOG);
                    }
                    boolean ok = true;
                    if (!permissionsItems.isEmpty()) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        if (preferences.getBoolean("firstlogin", true) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)) {
                            preferences.edit().putBoolean("firstlogin", false).commit();
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                            if (!allowCall && (!allowCancelCall || !allowReadCallLog)) {
                                builder.setMessage(LocaleController.getString("AllowReadCallAndLog", R.string.AllowReadCallAndLog));
                            } else if (!allowCancelCall || !allowReadCallLog) {
                                builder.setMessage(LocaleController.getString("AllowReadCallLog", R.string.AllowReadCallLog));
                            } else {
                                builder.setMessage(LocaleController.getString("AllowReadCall", R.string.AllowReadCall));
                            }
                            permissionsDialog = showDialog(builder.create());
                        } else {
                            try {
                                getParentActivity().requestPermissions(permissionsItems.toArray(new String[0]), 6);
                            } catch (Exception ignore) {
                                ok = false;
                            }
                        }
                        if (ok) {
                            return;
                        }
                    }
                }
            }

            if (countryState == 1) {
                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                return;
            } else if (countryState == 2 && !BuildVars.DEBUG_VERSION) {
                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("WrongCountry", R.string.WrongCountry));
                return;
            }
            if (codeField.length() == 0) {
                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                return;
            }
            if (phoneField.length() == 0) {
                onFieldError(phoneField);
                return;
            }
            String phone = PhoneFormat.stripExceptNumbers("" + codeField.getText() + phoneField.getText());
            boolean isTestBakcend = BuildVars.DEBUG_PRIVATE_VERSION && getConnectionsManager().isTestBackend();
            if (isTestBakcend != testBackend) {
                getConnectionsManager().switchBackend(false);
                isTestBakcend = testBackend;
            }
            if (getParentActivity() instanceof LaunchActivity) {
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    UserConfig userConfig = UserConfig.getInstance(a);
                    if (!userConfig.isClientActivated()) {
                        continue;
                    }
                    String userPhone = userConfig.getCurrentUser().phone;
                    if (PhoneNumberUtils.compare(phone, userPhone) && ConnectionsManager.getInstance(a).isTestBackend() == isTestBakcend) {
                        final int num = a;
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("AccountAlreadyLoggedIn", R.string.AccountAlreadyLoggedIn));
                        builder.setPositiveButton(LocaleController.getString("AccountSwitch", R.string.AccountSwitch), (dialog, which) -> {
                            if (UserConfig.selectedAccount != num) {
                                ((LaunchActivity) getParentActivity()).switchToAccount(num, false);
                            }
                            finishFragment();
                        });
                        builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), null);
                        showDialog(builder.create());
                        return;
                    }
                }
            }

            ConnectionsManager.getInstance(currentAccount).cleanup(false);
            final TLRPC.TL_auth_sendCode req = new TLRPC.TL_auth_sendCode();
            req.api_hash = BuildVars.APP_HASH;
            req.api_id = BuildVars.APP_ID;
            req.phone_number = phone;
            req.settings = new TLRPC.TL_codeSettings();
            req.settings.allow_flashcall = simcardAvailable && allowCall && allowCancelCall && allowReadCallLog;
            req.settings.allow_app_hash = PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices();;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            if (req.settings.allow_app_hash) {
                preferences.edit().putString("sms_hash", BuildVars.SMS_HASH).commit();
            } else {
                preferences.edit().remove("sms_hash").commit();
            }
            if (req.settings.allow_flashcall) {
                try {
                    String number = tm.getLine1Number();
                    if (!TextUtils.isEmpty(number)) {
                        req.settings.current_number = PhoneNumberUtils.compare(phone, number);
                        if (!req.settings.current_number) {
                            req.settings.allow_flashcall = false;
                        }
                    } else {
                        if (UserConfig.getActivatedAccountsCount() > 0) {
                            req.settings.allow_flashcall = false;
                        } else {
                            req.settings.current_number = false;
                        }
                    }
                } catch (Exception e) {
                    req.settings.allow_flashcall = false;
                    FileLog.e(e);
                }
            }
            final Bundle params = new Bundle();
            params.putString("phone", "+" + codeField.getText() + " " + phoneField.getText());
            try {
                params.putString("ephone", "+" + PhoneFormat.stripExceptNumbers(codeField.getText().toString()) + " " + PhoneFormat.stripExceptNumbers(phoneField.getText().toString()));
            } catch (Exception e) {
                FileLog.e(e);
                params.putString("ephone", "+" + phone);
            }
            params.putString("phoneFormated", phone);
            nextPressed = true;
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (error == null) {
                    analyticsCollector.onAppEvent(AppEvent.LOGIN_PHONE_INSERTED);
                    fillNextCodeParams(params, (TLRPC.TL_auth_sentCode) response);
                } else {
                    if (error.text != null) {
                        Log.d(TAG, "onNextPressed() error: " + error.text);
                        if (error.text.contains("PHONE_NUMBER_INVALID")) {
                            needShowInvalidAlert(LoginActivity.this, req.phone_number, false);
                        } else if (error.text.contains("PHONE_PASSWORD_FLOOD")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                        } else if (error.text.contains("PHONE_NUMBER_FLOOD")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("PhoneNumberFlood", R.string.PhoneNumberFlood));
                        } else if (error.text.contains("PHONE_NUMBER_BANNED")) {
                            needShowInvalidAlert(LoginActivity.this, req.phone_number, true);
                        } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidCode", R.string.InvalidCode));
                        } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("CodeExpired", R.string.CodeExpired));
                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                        } else if (error.code != -1000) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                        }
                    }
                }
                needHideProgress(false);
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagTryDifferentDc | ConnectionsManager.RequestFlagEnableUnauthorized);
            needShowProgress(reqId);
        }

        private boolean numberFilled;
        public void fillNumber() {
            if (numberFilled) {
                return;
            }
            try {
                TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm.getSimState() != TelephonyManager.SIM_STATE_ABSENT && tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE) {
                    boolean allowCall = true;
                    if (Build.VERSION.SDK_INT >= 23) {
                        allowCall = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
                        if (checkShowPermissions && !allowCall) {
                            permissionsShowItems.clear();
                            if (!allowCall) {
                                permissionsShowItems.add(Manifest.permission.READ_PHONE_STATE);
                            }
                            if (!permissionsShowItems.isEmpty()) {
                                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                                if (preferences.getBoolean("firstloginshow", true) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
                                    preferences.edit().putBoolean("firstloginshow", false).commit();
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                    builder.setMessage(LocaleController.getString("AllowFillNumber", R.string.AllowFillNumber));
                                    permissionsShowDialog = showDialog(builder.create());
                                    needRequestPermissions = true;
                                } else {
                                    getParentActivity().requestPermissions(permissionsShowItems.toArray(new String[0]), 7);
                                }
                            }
                            return;
                        }
                    }
                    numberFilled = true;
                    if (!newAccount && allowCall) {
                        String number = PhoneFormat.stripExceptNumbers(tm.getLine1Number());
                        String textToSet = null;
                        boolean ok = false;
                        if (!TextUtils.isEmpty(number)) {
                            if (number.length() > 4) {
                                for (int a = 4; a >= 1; a--) {
                                    String sub = number.substring(0, a);

                                    CountrySelectActivity.Country country;
                                    List<CountrySelectActivity.Country> list = codesMap.get(sub);
                                    if (list == null) {
                                        country = null;
                                    } else if (list.size() > 1) {
                                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                                        String lastMatched = preferences.getString("phone_code_last_matched_" + sub, null);

                                        country = list.get(list.size() - 1);
                                        if (lastMatched != null) {
                                            for (CountrySelectActivity.Country c : countriesArray) {
                                                if (Objects.equals(c.shortname, lastMatched)) {
                                                    country = c;
                                                    break;
                                                }
                                            }
                                        }
                                    } else {
                                        country = list.get(0);
                                    }

                                    if (country != null) {
                                        ok = true;
                                        textToSet = number.substring(a);
                                        codeField.setText(sub);
                                        break;
                                    }
                                }
                                if (!ok) {
                                    textToSet = number.substring(1);
                                    codeField.setText(number.substring(0, 1));
                                }
                            }
                            if (textToSet != null) {
                                phoneField.requestFocus();
                                phoneField.setText(textToSet);
                                phoneField.setSelection(phoneField.length());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void onShow() {
            super.onShow();
            Log.d(TAG, "onShow()");
            fillNumber();
            if (checkBoxCell != null) {
                checkBoxCell.setChecked(syncContacts, false);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (phoneField != null) {
                    if (needRequestPermissions) {
                        codeField.clearFocus();
                        phoneField.clearFocus();
                    } else {
                        if (!SbdvServiceLocator.getConfig().getQrCodeLoginEnabled()) {
                            if (codeField.length() != 0) {
                                phoneField.requestFocus();
                                phoneField.setSelection(phoneField.length());
                                AndroidUtilities.showKeyboard(phoneField);
                            } else {
                                codeField.requestFocus();
                                AndroidUtilities.showKeyboard(codeField);
                            }
                        }
                    }
                }
            }, 100);
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("YourPhone", R.string.YourPhone);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeField.getText().toString();
            if (code.length() != 0) {
                bundle.putString("phoneview_code", code);
            }
            String phone = phoneField.getText().toString();
            if (phone.length() != 0) {
                bundle.putString("phoneview_phone", phone);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            String code = bundle.getString("phoneview_code");
            if (code != null) {
                codeField.setText(code);
            }
            String phone = bundle.getString("phoneview_phone");
            if (phone != null) {
                phoneField.setText(phone);
            }
        }
    }

    public class LoginActivitySmsView extends SlideView implements NotificationCenter.NotificationCenterDelegate {

        private String phone;
        private String phoneHash;
        private String requestPhone;
        private String emailPhone;
        private LinearLayout codeFieldContainer;
        private EditTextBoldCursor[] codeField;
        private TextView confirmTextView;
        private TextView titleTextView;
        private ImageView blackImageView;
        private RLottieImageView blueImageView;
        private TextView timeText;
        private TextView problemText;
        private Bundle currentParams;
        private ProgressView progressView;

        RLottieDrawable hintDrawable;

        private Timer timeTimer;
        private Timer codeTimer;
        private int openTime;
        private final Object timerSync = new Object();
        private int time = 60000;
        private int codeTime = 15000;
        private double lastCurrentTime;
        private double lastCodeTime;
        private boolean ignoreOnTextChange;
        private boolean waitingForEvent;
        private boolean nextPressed;
        private String lastError = "";
        private int currentType;
        private int nextType;
        private String pattern = "*";
        private String catchedPhone;
        private int length;

        public LoginActivitySmsView(Context context, final int type) {
            super(context);

            currentType = type;
            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(getContext().getColor(R.color.white_alpha_90));
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getSmallTextSize());
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);

            titleTextView = new TextView(context);
            titleTextView.setTextColor(context.getColor(R.color.white));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getSubtitleTextSize());
            titleTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            titleTextView.setGravity(Gravity.TOP | Gravity.START);

            if (currentType == AUTH_TYPE_FLASH_CALL) {
                confirmTextView.setGravity(Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
                FrameLayout frameLayout = new FrameLayout(context);
                addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

                ImageView imageView = new ImageView(context);
                imageView.setImageResource(R.drawable.phone_activate);
                if (LocaleController.isRTL) {
                    if (!DeviceUtils.isSberDevices() && !DeviceUtils.isHuawei()) {
                        frameLayout.addView(imageView, LayoutHelper.createFrame(64, 76, Gravity.LEFT | Gravity.CENTER_VERTICAL, 2, 2, 0, 0));
                    }
                    frameLayout.addView(confirmTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 64 + 18, 0, 0, 0));
                } else {
                    frameLayout.addView(confirmTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 0, 64 + 18, 0));
                    if (!DeviceUtils.isSberDevices() && !DeviceUtils.isHuawei()) {
                        frameLayout.addView(imageView, LayoutHelper.createFrame(64, 76, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 2, 0, 2));
                    }
                }
            } else {
                confirmTextView.setGravity(Gravity.TOP | Gravity.START);

                FrameLayout frameLayout = new FrameLayout(context);
                addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

                if (currentType == AUTH_TYPE_MESSAGE) {
                    if (!DeviceUtils.isSberDevices() && !DeviceUtils.isHuawei()) {
                        blackImageView = new ImageView(context);
                        blackImageView.setImageResource(R.drawable.sms_devices);
                        blackImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
                        frameLayout.addView(blackImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

                        blueImageView = new RLottieImageView(context);
                        blueImageView.setImageResource(R.drawable.sms_bubble);
                        blueImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionBackground), PorterDuff.Mode.MULTIPLY));
                        frameLayout.addView(blueImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
                    }
                    titleTextView.setText(LocaleController.getString("SentAppCodeTitle", R.string.SentAppCodeTitle));
                } else {
                    if (!DeviceUtils.isSberDevices() && !DeviceUtils.isHuawei()) {
                        blueImageView = new RLottieImageView(context);
                        hintDrawable = new RLottieDrawable(R.raw.sms_incoming_info, "" + R.raw.sms_incoming_info, AndroidUtilities.dp(64), AndroidUtilities.dp(64), true, null);
                        hintDrawable.setLayerColor("Bubble.**", Theme.getColor(Theme.key_chats_actionBackground));
                        hintDrawable.setLayerColor("Phone.**", Theme.getColor(Theme.key_chats_actionBackground));
                        blueImageView.setAnimation(hintDrawable);
                        frameLayout.addView(blueImageView, LayoutHelper.createFrame(64, 64, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
                    }
                    titleTextView.setText(LocaleController.getString("SentSmsCodeTitle", R.string.SentSmsCodeTitle));
                }
                addView(titleTextView, LayoutHelper.createLinear(
                        LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP,
                        0, TelegramDimensions.selectSize(12, 0), 0, 0)
                );
                addView(confirmTextView, LayoutHelper.createLinear(
                        LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP,
                        0,
                        TelegramDimensions.calculateSize(10),
                        0,
                        0)
                );
            }

            codeFieldContainer = new LinearLayout(context);
            codeFieldContainer.setOrientation(HORIZONTAL);
            addView(codeFieldContainer, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL,
                    0,
                    TelegramDimensions.selectSize(8, 12),
                    0,
                    0)
            );
            if (currentType == AUTH_TYPE_FLASH_CALL) {
                codeFieldContainer.setVisibility(GONE);
            }

            timeText = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.AT_MOST));
                }
            };
            timeText.setTextColor(getContext().getColor(R.color.white_alpha_90));
            timeText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            if (currentType == AUTH_TYPE_FLASH_CALL) {
                timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getSmallTextSize());
                addView(timeText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

                progressView = new ProgressView(context);
                timeText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                addView(progressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 3, 0, 12, 0, 0));
            } else {
                timeText.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(10));
                timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getSmallTextSize());
                timeText.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                addView(timeText,
                        LayoutHelper.createLinear(
                                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                                0,
                                TelegramDimensions.selectSize(12, 12),
                                0,
                                0)
                );
            }

            int problemTextViewPadding = AndroidUtilities.dp(12);
            problemText = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.AT_MOST));
                }
            };
            problemText.setBackground(getResources().getDrawable(R.drawable.sbdv_selector_focusable_stroke_24));
            problemText.setTextColor(context.getResources().getColor(R.color.sbdv_green_light));
            problemText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            problemText.setPadding(problemTextViewPadding, problemTextViewPadding, problemTextViewPadding, problemTextViewPadding);
            problemText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getSmallTextSize());
            problemText.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            problemText.setText(LocaleController.getString("DidNotGetTheCodeSms", R.string.DidNotGetTheCodeSms));

            addView(problemText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16, 0, 0));
            problemText.setOnClickListener(v -> {
                if (nextPressed) {
                    return;
                }
                boolean email = nextType == AUTH_TYPE_CALL && currentType == AUTH_TYPE_SMS || nextType == 0;
                if (!email) {
                    if (doneProgressView.getTag() != null) {
                        return;
                    }
                    resendCode();
                } else {
                    try {
                        PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                        String version = String.format(Locale.US, "%s (%d)", pInfo.versionName, pInfo.versionCode);

                        Intent mailer = new Intent(Intent.ACTION_SENDTO);
                        mailer.setData(Uri.parse("mailto:"));
                        mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{"reports@stel.com"});
                        mailer.putExtra(Intent.EXTRA_SUBJECT, "Android registration/login issue " + version + " " + emailPhone);
                        mailer.putExtra(Intent.EXTRA_TEXT, "Phone: " + requestPhone + "\nApp version: " + version + "\nOS version: SDK " + Build.VERSION.SDK_INT + "\nDevice Name: " + Build.MANUFACTURER + Build.MODEL + "\nLocale: " + Locale.getDefault() + "\nError: " + lastError);
                        getContext().startActivity(Intent.createChooser(mailer, "Send email..."));
                    } catch (Exception e) {
                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("NoMailInstalled", R.string.NoMailInstalled));
                    }
                }
            });
            problemText.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    int i = 0;
                    for (; i < codeField.length; i++) {
                        String fieldText = codeField[i].getText().toString();
                        if (fieldText.isEmpty()) {
                            codeField[i].requestFocus();
                            return true;
                        }
                    }
                    // all fields are not empty
                    if (i > 0) {
                        codeField[codeField.length - 1].requestFocus();
                        return true;
                    }
                }
                return false;
            });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (currentType != AUTH_TYPE_FLASH_CALL && blueImageView != null) {
                int innerHeight = blueImageView.getMeasuredHeight() + titleTextView.getMeasuredHeight() + confirmTextView.getMeasuredHeight() + AndroidUtilities.dp(18 + 17);
                int requiredHeight = AndroidUtilities.dp(80);
                int maxHeight = AndroidUtilities.dp(291);
                if (scrollHeight - innerHeight < requiredHeight) {
                    setMeasuredDimension(getMeasuredWidth(), innerHeight + requiredHeight);
                } else {
                    setMeasuredDimension(getMeasuredWidth(), Math.min(scrollHeight, maxHeight));
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (currentType != AUTH_TYPE_FLASH_CALL && blueImageView != null) {
                int bottom = confirmTextView.getBottom();
                int height = getMeasuredHeight() - bottom;

                int h;
                if (problemText.getVisibility() == VISIBLE) {
                    h = problemText.getMeasuredHeight();
                    t = bottom + height - h;
                    problemText.layout(problemText.getLeft(), t, problemText.getRight(), t + h);
                } else if (timeText.getVisibility() == VISIBLE) {
                    h = timeText.getMeasuredHeight();
                    t = bottom + height - h;
                    timeText.layout(timeText.getLeft(), t, timeText.getRight(), t + h);
                } else {
                    t = bottom + height;
                }

                height = t - bottom;
                h = codeFieldContainer.getMeasuredHeight();
                t = (height - h) / 2 + bottom;
                codeFieldContainer.layout(codeFieldContainer.getLeft(), t, codeFieldContainer.getRight(), t + h);
            }
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        private void resendCode() {
            final Bundle params = new Bundle();
            params.putString("phone", phone);
            params.putString("ephone", emailPhone);
            params.putString("phoneFormated", requestPhone);

            nextPressed = true;

            TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
            req.phone_number = requestPhone;
            req.phone_code_hash = phoneHash;
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (error == null) {
                    fillNextCodeParams(params, (TLRPC.TL_auth_sentCode) response);
                } else {
                    if (error.text != null) {
                        if (error.text.contains("PHONE_NUMBER_INVALID")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                        } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidCode", R.string.InvalidCode));
                        } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                            onBackPressed(true);
                            setPage(VIEW_PHONE_INPUT, true, null, true);
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("CodeExpired", R.string.CodeExpired));
                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                        } else if (error.code != -1000) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                        }
                    }
                }
                needHideProgress(false);
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            needShowProgress(0);
        }

        @Override
        public String getHeaderName() {
            if (currentType == AUTH_TYPE_MESSAGE) {
                return phone;
            } else {
                return LocaleController.getString("YourCode", R.string.YourCode);
            }
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            waitingForEvent = true;
            if (currentType == AUTH_TYPE_SMS) {
                AndroidUtilities.setWaitingForSms(true);
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                AndroidUtilities.setWaitingForCall(true);
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveCall);
            }

            currentParams = params;
            phone = params.getString("phone");
            emailPhone = params.getString("ephone");
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            time = params.getInt("timeout");
            openTime = (int) (System.currentTimeMillis() / 1000);
            nextType = params.getInt("nextType");
            pattern = params.getString("pattern");
            length = params.getInt("length");
            if (length == 0) {
                length = 5;
            }

            if (codeField == null || codeField.length != length) {
                codeField = new EditTextBoldCursor[length];
                for (int a = 0; a < length; a++) {
                    final int num = a;
                    codeField[a] = new EditTextBoldCursor(getContext());
                    codeField[a].setTextColor(getContext().getColor(R.color.white));
                    codeField[a].setCursorColor(getContext().getColor(R.color.transparent));
                    codeField[a].setCursorSize(AndroidUtilities.dp(TelegramDimensions.getPhoneLoginEditTextButtonTextSize()));
                    codeField[a].setCursorWidth(1.5f);
                    codeField[a].setNextFocusUpId(R.id.actionbar_back_btn);
                    codeField[a].setOnFocusChangeListener((v, hasFocus) -> {
                        if (hasFocus) {
                            Drawable pressedDrawable = v.getBackground().mutate();
                            pressedDrawable.setColorFilter(new PorterDuffColorFilter(getContext().getColor(R.color.sbdv_green_light), PorterDuff.Mode.MULTIPLY));
                            v.setBackgroundDrawable(pressedDrawable);
                        } else {
                            Drawable pressedDrawable = v.getBackground().mutate();
                            pressedDrawable.setColorFilter(new PorterDuffColorFilter(getContext().getColor(R.color.sbdv_white_28), PorterDuff.Mode.MULTIPLY));
                            v.setBackgroundDrawable(pressedDrawable);
                        }
                    });

                    Drawable pressedDrawable = getResources().getDrawable(R.drawable.search_dark_activated).mutate();
                    pressedDrawable.setColorFilter(new PorterDuffColorFilter(getContext().getColor(R.color.sbdv_white_28), PorterDuff.Mode.MULTIPLY));

                    codeField[a].setBackgroundDrawable(pressedDrawable);
                    codeField[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                    codeField[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getPhoneLoginEditTextButtonTextSize());
                    codeField[a].setMaxLines(1);
                    codeField[a].setPadding(0, 0, 0, 0);
                    codeField[a].setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                    if (currentType == AUTH_TYPE_FLASH_CALL) {
                        codeField[a].setEnabled(false);
                        codeField[a].setInputType(InputType.TYPE_NULL);
                        codeField[a].setVisibility(GONE);
                    } else {
                        codeField[a].setInputType(InputType.TYPE_CLASS_NUMBER);
                    }
                    codeFieldContainer.addView(codeField[a],
                            LayoutHelper.createLinear(
                                    TelegramDimensions.calculateSize(32),
                                    TelegramDimensions.selectSize(28 , TelegramDimensions.calculateSize(24)),
                                    Gravity.CENTER_HORIZONTAL,
                                    0,
                                    TelegramDimensions.calculateSize(8),
                                    a != length - 1 ? 7 : 0,
                                    0)
                    );
                    codeField[a].addTextChangedListener(new TextWatcher() {

                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            if (ignoreOnTextChange) {
                                return;
                            }
                            int len = s.length();
                            if (len >= 1) {
                                if (len > 1) {
                                    String text = s.toString();
                                    ignoreOnTextChange = true;
                                    for (int a = 0; a < Math.min(length - num, len); a++) {
                                        if (a == 0) {
                                            s.replace(0, len, text.substring(a, a + 1));
                                        } else {
                                            codeField[num + a].setText(text.substring(a, a + 1));
                                        }
                                    }
                                    ignoreOnTextChange = false;
                                }

                                if (num != length - 1) {
                                    codeField[num + 1].setSelection(codeField[num + 1].length());
                                    codeField[num + 1].requestFocus();
                                }
                                if ((num == length - 1 || num == length - 2 && len >= 2) && getCode().length() == length) {
                                    onNextPressed(null);
                                }
                            }
                        }
                    });
                    codeField[a].setOnKeyListener((v, keyCode, event) -> {
                        if (keyCode == KeyEvent.KEYCODE_DEL && codeField[num].length() == 0 && num > 0) {
                            codeField[num - 1].setSelection(codeField[num - 1].length());
                            codeField[num - 1].requestFocus();
                            codeField[num - 1].dispatchKeyEvent(event);
                            return true;
                        }
                        return false;
                    });
                    codeField[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                        if (i == EditorInfo.IME_ACTION_NEXT) {
                            onNextPressed(null);
                            return true;
                        }
                        return false;
                    });
                }
            } else {
                for (int a = 0; a < codeField.length; a++) {
                    codeField[a].setText("");
                }
            }

            if (progressView != null) {
                progressView.setVisibility(nextType != 0 ? VISIBLE : GONE);
            }

            if (phone == null) {
                return;
            }

            String number = PhoneFormat.getInstance().format(phone);
            CharSequence str = "";
            if (currentType == AUTH_TYPE_MESSAGE) {
                str = AndroidUtilities.replaceTags(LocaleController.getString("SentAppCode", R.string.SentAppCode));
            } else if (currentType == AUTH_TYPE_SMS) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentSmsCode", R.string.SentSmsCode, LocaleController.addNbsp(number)));
            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentCallCode", R.string.SentCallCode, LocaleController.addNbsp(number)));
            } else if (currentType == AUTH_TYPE_CALL) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentCallOnly", R.string.SentCallOnly, LocaleController.addNbsp(number)));
            }
            confirmTextView.setText(str);

            if (currentType != AUTH_TYPE_FLASH_CALL) {
                AndroidUtilities.showKeyboard(codeField[0]);
                codeField[0].requestFocus();
            } else {
                AndroidUtilities.hideKeyboard(codeField[0]);
            }

            destroyTimer();
            destroyCodeTimer();

            lastCurrentTime = System.currentTimeMillis();
            if (currentType == AUTH_TYPE_MESSAGE) {
                if (nextType == 0) {
                    problemText.setVisibility(GONE);
                    timeText.setVisibility(GONE);
                } else {
                    problemText.setVisibility(VISIBLE);
                    timeText.setVisibility(GONE);
                }
            } else if (currentType == AUTH_TYPE_FLASH_CALL && (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS)) {
                problemText.setVisibility(GONE);
                timeText.setVisibility(VISIBLE);
                if (nextType == AUTH_TYPE_CALL) {
                    timeText.setText(LocaleController.formatString("CallText", R.string.CallText, 1, 0));
                } else if (nextType == AUTH_TYPE_SMS) {
                    timeText.setText(LocaleController.formatString("SmsText", R.string.SmsText, 1, 0));
                }
                String callLogNumber = restore ? AndroidUtilities.obtainLoginPhoneCall(pattern) : null;
                if (callLogNumber != null) {
                    ignoreOnTextChange = true;
                    codeField[0].setText(callLogNumber);
                    ignoreOnTextChange = false;
                    onNextPressed(null);
                } else if (catchedPhone != null) {
                    ignoreOnTextChange = true;
                    codeField[0].setText(catchedPhone);
                    ignoreOnTextChange = false;
                    onNextPressed(null);
                } else {
                    createTimer();
                }
            } else if (currentType == AUTH_TYPE_SMS && (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_FLASH_CALL)) {
                timeText.setText(LocaleController.formatString("CallText", R.string.CallText, 2, 0));
                problemText.setVisibility(GONE);
                timeText.setVisibility(time < 1000 ? GONE : VISIBLE);

                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                String hash = preferences.getString("sms_hash", null);
                String savedCode = null;
                if (!TextUtils.isEmpty(hash)) {
                    savedCode = preferences.getString("sms_hash_code", null);
                    if (savedCode != null && savedCode.contains(hash + "|")) {
                        savedCode = savedCode.substring(savedCode.indexOf('|') + 1);
                    } else {
                        savedCode = null;
                    }
                }
                if (savedCode != null) {
                    codeField[0].setText(savedCode);
                    onNextPressed(null);
                } else {
                    createTimer();
                }
            } else if (currentType == AUTH_TYPE_CALL && nextType == AUTH_TYPE_SMS) {
                timeText.setText(LocaleController.formatString("SmsText", R.string.SmsText, 2, 0));
                problemText.setVisibility(GONE);
                timeText.setVisibility(time < 1000 ? GONE : VISIBLE);
                createTimer();
            } else {
                timeText.setVisibility(GONE);
                problemText.setVisibility(GONE);
                createCodeTimer();
            }
        }

        private void createCodeTimer() {
            if (codeTimer != null) {
                return;
            }
            codeTime = 15000;
            codeTimer = new Timer();
            lastCodeTime = System.currentTimeMillis();
            codeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    AndroidUtilities.runOnUIThread(() -> {
                        double currentTime = System.currentTimeMillis();
                        double diff = currentTime - lastCodeTime;
                        lastCodeTime = currentTime;
                        codeTime -= diff;
                        if (codeTime <= 1000) {
                            // skip making problem text visible if it was gone, only hide time text
                            timeText.setVisibility(GONE);
                            destroyCodeTimer();
                        }
                    });
                }
            }, 0, 1000);
        }

        private void destroyCodeTimer() {
            try {
                synchronized (timerSync) {
                    if (codeTimer != null) {
                        codeTimer.cancel();
                        codeTimer = null;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        private void createTimer() {
            if (timeTimer != null) {
                return;
            }
            if (progressView != null) {
                progressView.resetProgressAnimation();
            }
            timeTimer = new Timer();
            timeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (timeTimer == null) {
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        final double currentTime = System.currentTimeMillis();
                        double diff = currentTime - lastCurrentTime;
                        lastCurrentTime = currentTime;
                        time -= diff;
                        if (time >= 1000) {
                            int minutes = time / 1000 / 60;
                            int seconds = time / 1000 - minutes * 60;
                            if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_FLASH_CALL) {
                                timeText.setText(LocaleController.formatString("CallText", R.string.CallText, minutes, seconds));
                            } else if (nextType == AUTH_TYPE_SMS) {
                                timeText.setText(LocaleController.formatString("SmsText", R.string.SmsText, minutes, seconds));
                            }
                            if (progressView != null && !progressView.isProgressAnimationRunning()) {
                                progressView.startProgressAnimation(time - 1000L);
                            }
                        } else {
                            destroyTimer();
                            if (currentType == AUTH_TYPE_FLASH_CALL) {
                                AndroidUtilities.setWaitingForCall(false);
                                NotificationCenter.getGlobalInstance().removeObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveCall);
                                waitingForEvent = false;
                                destroyCodeTimer();
                                resendCode();
                            } else if (currentType == AUTH_TYPE_SMS || currentType == AUTH_TYPE_CALL) {
                                if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS) {
                                    if (nextType == AUTH_TYPE_CALL) {
                                        timeText.setText(LocaleController.getString("Calling", R.string.Calling));
                                    } else {
                                        timeText.setText(LocaleController.getString("SendingSms", R.string.SendingSms));
                                    }
                                    createCodeTimer();
                                    TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
                                    req.phone_number = requestPhone;
                                    req.phone_code_hash = phoneHash;
                                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                                        if (error != null && error.text != null) {
                                            AndroidUtilities.runOnUIThread(() -> lastError = error.text);
                                        }
                                    }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                                } else if (nextType == AUTH_TYPE_FLASH_CALL) {
                                    AndroidUtilities.setWaitingForSms(false);
                                    NotificationCenter.getGlobalInstance().removeObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                                    waitingForEvent = false;
                                    destroyCodeTimer();
                                    resendCode();
                                }
                            }
                        }
                    });
                }
            }, 0, 1000);
        }

        private void destroyTimer() {
            try {
                synchronized (timerSync) {
                    if (timeTimer != null) {
                        timeTimer.cancel();
                        timeTimer = null;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        private String getCode() {
            if (codeField == null) {
                return "";
            }
            StringBuilder codeBuilder = new StringBuilder();
            for (int a = 0; a < codeField.length; a++) {
                codeBuilder.append(PhoneFormat.stripExceptNumbers(codeField[a].getText().toString()));
            }
            return codeBuilder.toString();
        }

        @Override
        public void onNextPressed(String code) {
            Log.d(TAG, "onNextPressed() in SmsView");
            if (nextPressed || currentViewNum < VIEW_CODE_MESSAGE || currentViewNum > VIEW_CODE_CALL) {
                return;
            }

            if (code == null) {
                code = getCode();
            }
            if (TextUtils.isEmpty(code)) {
                onFieldError(codeFieldContainer);
                return;
            }
            nextPressed = true;
            if (currentType == AUTH_TYPE_SMS) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            final TLRPC.TL_auth_signIn req = new TLRPC.TL_auth_signIn();
            req.phone_number = requestPhone;
            req.phone_code = code;
            req.phone_code_hash = phoneHash;
            req.flags |= 1;
            destroyTimer();
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                boolean ok = false;
                if (error == null) {
                    nextPressed = false;
                    ok = true;
                    analyticsCollector.onAppEvent(AppEvent.LOGIN_VERIFICATION_CODE_INSERTED);
                    Config config = SbdvServiceLocator.getConfig();
                    if (!config.getIntegratedWithSingleCallsPlace()) {
                        showDoneButton(false, true);
                    }
                    destroyTimer();
                    destroyCodeTimer();
                    if (response instanceof TLRPC.TL_auth_authorizationSignUpRequired) {
                        if (SbdvServiceLocator.getConfig().isReducedRegistrationEnabled()) {
                            needShowCantRegisterAlert();
                        } else {
                            TLRPC.TL_auth_authorizationSignUpRequired authorization = (TLRPC.TL_auth_authorizationSignUpRequired) response;
                            if (authorization.terms_of_service != null) {
                                currentTermsOfService = authorization.terms_of_service;
                            }
                            Bundle params = new Bundle();
                            params.putString("phoneFormated", requestPhone);
                            params.putString("phoneHash", phoneHash);
                            params.putString("code", req.phone_code);
                            setPage(VIEW_REGISTER, true, params, false);
                        }
                    } else {
                        onAuthSuccess((TLRPC.TL_auth_authorization) response);
                    }
                } else {
                    lastError = error.text;
                    if (error != null && error.text != null) Log.w(TAG, error.text);
                    if (error.text.contains("SESSION_PASSWORD_NEEDED")) {
                        ok = true;
                        TLRPC.TL_account_getPassword req2 = new TLRPC.TL_account_getPassword();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            nextPressed = false;
                            showDoneButton(false, true);
                            if (error1 == null) {
                                analyticsCollector.onAppEvent(AppEvent.LOGIN_VERIFICATION_CODE_INSERTED);
                                TLRPC.account_Password password = (TLRPC.account_Password) response1;
                                if (!TwoStepVerificationActivity.canHandleCurrentPassword(password, true)) {
                                    AlertsCreator.showUpdateAppAlert(getParentActivity(), LocaleController.getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                                    return;
                                }
                                Bundle bundle = new Bundle();
                                SerializedData data = new SerializedData(password.getObjectSize());
                                password.serializeToStream(data);
                                bundle.putString("password", Utilities.bytesToHex(data.toByteArray()));
                                bundle.putString("phoneFormated", requestPhone);
                                bundle.putString("phoneHash", phoneHash);
                                bundle.putString("code", req.phone_code);
                                setPage(VIEW_PASSWORD, true, bundle, false);
                            } else {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), error1.text);
                            }
                        }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                        destroyTimer();
                        destroyCodeTimer();
                    } else {
                        nextPressed = false;
                        showDoneButton(false, true);
                        if (currentType == AUTH_TYPE_FLASH_CALL && (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS) ||
                                currentType == AUTH_TYPE_SMS && (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_FLASH_CALL) ||
                                currentType == AUTH_TYPE_CALL && nextType == AUTH_TYPE_SMS) {
                            createTimer();
                        }
                        if (currentType == AUTH_TYPE_SMS) {
                            AndroidUtilities.setWaitingForSms(true);
                            NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                        } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                            AndroidUtilities.setWaitingForCall(true);
                            NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveCall);
                        }
                        waitingForEvent = true;

                        if (currentType != AUTH_TYPE_FLASH_CALL) {
                            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                            } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidCode", R.string.InvalidCode));
                                for (int a = 0; a < codeField.length; a++) {
                                    codeField[a].setText("");
                                }
                                codeField[0].requestFocus();
                            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                onBackPressed(true);
                                setPage(VIEW_PHONE_INPUT, true, null, true);
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("CodeExpired", R.string.CodeExpired));
                            } else if (error.text.startsWith("FLOOD_WAIT")) {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                            } else {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                            }
                        }
                    }
                }
                if (ok) {
                    if (currentType == AUTH_TYPE_FLASH_CALL) {
                        AndroidUtilities.endIncomingCall();
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            needShowProgress(reqId, false);
            showDoneButton(true, true);
        }

        @Override
        public boolean onBackPressed(boolean force) {
            if (!force) {
                View dialogView = inflate(getContext(), R.layout.sbdv_alert_dialog, null);
                AlertDialog alertDialog = new AlertDialog.Builder(getContext())
                        .setView(dialogView)
                        .show();

                alertDialog.setBackgroundColor(Color.TRANSPARENT);
                TextView title = dialogView.findViewById(R.id.alertTitle);
                TextView message = dialogView.findViewById(R.id.alertMessage);
                TextView positiveButton = dialogView.findViewById(R.id.positiveButton);
                TextView negativeButton = dialogView.findViewById(R.id.negativeButton);

                title.setText(LocaleController.getString("AppName", R.string.AppName));
                message.setText(LocaleController.getString("StopVerification", R.string.StopVerification));
                positiveButton.setText(LocaleController.getString("Continue", R.string.Continue), null);
                positiveButton.setOnClickListener((View v) -> alertDialog.dismiss());
                negativeButton.setVisibility(View.VISIBLE);
                negativeButton.setText(LocaleController.getString("Stop", R.string.Stop));
                negativeButton.setOnClickListener((view) -> {
                    alertDialog.dismiss();
                    onBackPressed(true);
                    setPage(VIEW_PHONE_INPUT, true, null, true);
                });
                return false;
            }
            nextPressed = false;
            needHideProgress(true);
            TLRPC.TL_auth_cancelCode req = new TLRPC.TL_auth_cancelCode();
            req.phone_number = requestPhone;
            req.phone_code_hash = phoneHash;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);

            destroyTimer();
            destroyCodeTimer();
            currentParams = null;
            if (currentType == AUTH_TYPE_SMS) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            return true;
        }

        @Override
        public void onDestroyActivity() {
            super.onDestroyActivity();
            if (currentType == AUTH_TYPE_SMS) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            destroyTimer();
            destroyCodeTimer();
        }

        @Override
        public void onShow() {
            super.onShow();
            if (currentType == AUTH_TYPE_FLASH_CALL) {
                return;
            }
            if (hintDrawable != null) {
                hintDrawable.setCurrentFrame(0);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (codeField != null) {
                    for (int a = codeField.length - 1; a >= 0; a--) {
                        if (a == 0 || codeField[a].length() != 0) {
                            codeField[a].requestFocus();
                            codeField[a].setSelection(codeField[a].length());
                            AndroidUtilities.showKeyboard(codeField[a]);
                            break;
                        }
                    }
                }
                if (hintDrawable != null) {
                    hintDrawable.start();
                }
            }, 100);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (!waitingForEvent || codeField == null) {
                return;
            }
            if (id == NotificationCenter.didReceiveSmsCode) {
                codeField[0].setText("" + args[0]);
                onNextPressed(null);
            } else if (id == NotificationCenter.didReceiveCall) {
                String num = "" + args[0];
                if (!AndroidUtilities.checkPhonePattern(pattern, num)) {
                    return;
                }
                if (!pattern.equals("*")) {
                    catchedPhone = num;
                    AndroidUtilities.endIncomingCall();
                }
                ignoreOnTextChange = true;
                codeField[0].setText(num);
                ignoreOnTextChange = false;
                onNextPressed(null);
            }
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = getCode();
            if (code.length() != 0) {
                bundle.putString("smsview_code_" + currentType, code);
            }
            if (catchedPhone != null) {
                bundle.putString("catchedPhone", catchedPhone);
            }
            if (currentParams != null) {
                bundle.putBundle("smsview_params_" + currentType, currentParams);
            }
            if (time != 0) {
                bundle.putInt("time", time);
            }
            if (openTime != 0) {
                bundle.putInt("open", openTime);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("smsview_params_" + currentType);
            if (currentParams != null) {
                setParams(currentParams, true);
            }
            String catched = bundle.getString("catchedPhone");
            if (catched != null) {
                catchedPhone = catched;
            }
            String code = bundle.getString("smsview_code_" + currentType);
            if (code != null && codeField != null) {
                codeField[0].setText(code);
            }
            int t = bundle.getInt("time");
            if (t != 0) {
                time = t;
            }
            int t2 = bundle.getInt("open");
            if (t2 != 0) {
                openTime = t2;
            }
        }
    }

    public class LoginActivityPasswordView extends SlideView {

        private EditTextBoldCursor codeField;
        private TextView confirmTextView;
        private TextView resetAccountButton;
        private TextView resetAccountText;
        private TextView cancelButton;

        private Bundle currentParams;
        private boolean nextPressed;
        private TLRPC.account_Password currentPassword;
        private String passwordString;
        private String requestPhone;
        private String phoneHash;
        private String phoneCode;

        //sbdv ibfo
        //2 ФА
        public LoginActivityPasswordView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            confirmTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(1), 1.0f);
            boolean reducedRegistrationEnabled = SbdvServiceLocator.getConfig().isReducedRegistrationEnabled();
            String confirmText = reducedRegistrationEnabled
                    ? LocaleController.getString("LoginPasswordTextHidden", R.string.LoginPasswordTextHidden)
                    : LocaleController.getString("LoginPasswordText", R.string.LoginPasswordText);
            confirmTextView.setText(confirmText);
            confirmTextView.setTextColor(getContext().getColor(R.color.white_alpha_90));
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getConfirmTextSize());
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

            codeField = new EditTextBoldCursor(context);
            codeField.setCursorSize(AndroidUtilities.dp(TelegramDimensions.getPhoneLoginEditTextButtonTextSize()));
            codeField.setCursorWidth(1.5f);
            codeField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            codeField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            codeField.setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            codeField.setTextColor(getContext().getColor(R.color.white_alpha_90));
            codeField.setCursorColor(getContext().getColor(R.color.white_alpha_90));
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getPhoneLoginEditTextButtonTextSize());
            codeField.setMaxLines(1);
            codeField.setPadding(0, 0, 0, 0);
            codeField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            codeField.setTransformationMethod(PasswordTransformationMethod.getInstance());
            codeField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            codeField.getInputExtras(true).putBoolean("ru.sberdevices.app.starkeyboard.voiceInputEnabled", false);
            addView(codeField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));
            codeField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });

            cancelButton = new TextView(context);
            if (!reducedRegistrationEnabled) {
                int cancelButtonPaddingDp = AndroidUtilities.dp(12);
                cancelButton.setBackground(getResources().getDrawable(R.drawable.sbdv_selector_focusable_stroke_24));
                cancelButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                cancelButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                cancelButton.setText(LocaleController.getString("ForgotPassword", R.string.ForgotPassword));
                cancelButton.setTextColor(Theme.getColor(Theme.key_chats_actionBackground));
                cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getPhoneLoginButtonTitle());
                cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
                cancelButton.setPadding(cancelButtonPaddingDp, cancelButtonPaddingDp, cancelButtonPaddingDp, cancelButtonPaddingDp);
                addView(cancelButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 20, 0, 0));
                cancelButton.setOnClickListener(view -> {
                    if (doneProgressView.getTag() != null) {
                        return;
                    }
                    if (currentPassword.has_recovery) {
                        needShowProgress(0);
                        TLRPC.TL_auth_requestPasswordRecovery req = new TLRPC.TL_auth_requestPasswordRecovery();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            needHideProgress(false);
                            if (error == null) {
                                final TLRPC.TL_auth_passwordRecovery res = (TLRPC.TL_auth_passwordRecovery) response;
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                View dialogView = inflate(getContext(), R.layout.sbdv_alert_dialog, null);
                                builder.setView(dialogView);
                                AlertDialog alertDialog = builder.create();
                                alertDialog.setBackgroundColor(Color.TRANSPARENT);
                                Dialog dialog = showDialog(alertDialog);
                                if (dialog != null) {
                                    dialog.setCanceledOnTouchOutside(false);
                                    dialog.setCancelable(false);
                                }
                                TextView title = dialogView.findViewById(R.id.alertTitle);
                                title.setText(LocaleController.getString("RestoreEmailSentTitle", R.string.RestoreEmailSentTitle));

                                TextView message = dialogView.findViewById(R.id.alertMessage);
                                message.setText(LocaleController.formatString("RestoreEmailSent", R.string.RestoreEmailSent, res.email_pattern));

                                TextView positiveButton = dialogView.findViewById(R.id.positiveButton);
                                positiveButton.setOnClickListener((View v) -> {
                                    Bundle bundle = new Bundle();
                                    bundle.putString("email_unconfirmed_pattern", res.email_pattern);
                                    setPage(VIEW_RECOVER, true, bundle, false);
                                    dialog.dismiss();
                                });
                            } else {
                                if (error.text != null) Log.w(TAG, error.text);
                                if (error.text.startsWith("FLOOD_WAIT")) {
                                    int time = Utilities.parseInt(error.text);
                                    String timeString;
                                    if (time < 60) {
                                        timeString = LocaleController.formatPluralString("Seconds", time);
                                    } else {
                                        timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                    }
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                } else {
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                                }
                            }
                        }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    } else {
                        resetAccountText.setVisibility(VISIBLE);
                        resetAccountButton.setVisibility(VISIBLE);
                        AndroidUtilities.hideKeyboard(codeField);
                        needShowAlert(LocaleController.getString("RestorePasswordNoEitle", R.string.RestorePasswordNoEmailTitle), LocaleController.getString("RestorePasswordNoEmailText", R.string.RestorePasswordNoEmailText));
                    }
                });
            }

            resetAccountButton = new TextView(context);
            resetAccountText = new TextView(context);
            if (!reducedRegistrationEnabled) {
                resetAccountButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                resetAccountButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                resetAccountButton.setVisibility(GONE);
                resetAccountButton.setText(LocaleController.getString("ResetMyAccount", R.string.ResetMyAccount));
                resetAccountButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                resetAccountButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                resetAccountButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
                resetAccountButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
                addView(resetAccountButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 34, 0, 0));
                resetAccountButton.setOnClickListener(view -> {
                    if (doneProgressView.getTag() != null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("ResetMyAccountWarningText", R.string.ResetMyAccountWarningText));
                    builder.setTitle(LocaleController.getString("ResetMyAccountWarning", R.string.ResetMyAccountWarning));
                    builder.setPositiveButton(LocaleController.getString("ResetMyAccountWarningReset", R.string.ResetMyAccountWarningReset), (dialogInterface, i) -> {
                        needShowProgress(0);
                        TLRPC.TL_account_deleteAccount req = new TLRPC.TL_account_deleteAccount();
                        req.reason = "Forgot password";
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            needHideProgress(false);
                            if (error == null) {
                                Bundle params = new Bundle();
                                params.putString("phoneFormated", requestPhone);
                                params.putString("phoneHash", phoneHash);
                                params.putString("code", phoneCode);
                                setPage(VIEW_REGISTER, true, params, false);
                            } else {
                                if (error.text.equals("2FA_RECENT_CONFIRM")) {
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ResetAccountCancelledAlert", R.string.ResetAccountCancelledAlert));
                                } else if (error.text.startsWith("2FA_CONFIRM_WAIT_")) {
                                    Bundle params = new Bundle();
                                    params.putString("phoneFormated", requestPhone);
                                    params.putString("phoneHash", phoneHash);
                                    params.putString("code", phoneCode);
                                    params.putInt("startTime", ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                                    params.putInt("waitTime", Utilities.parseInt(error.text.replace("2FA_CONFIRM_WAIT_", "")));
                                    setPage(VIEW_RESET_WAIT, true, params, false);
                                } else {
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                                }
                            }
                        }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                });

                resetAccountText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                resetAccountText.setVisibility(GONE);
                resetAccountText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
                resetAccountText.setText(LocaleController.getString("ResetMyAccountText", R.string.ResetMyAccountText));
                resetAccountText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                resetAccountText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
                addView(resetAccountText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 7, 0, 14));
            }
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("LoginPassword", R.string.LoginPassword);
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            if (params.isEmpty()) {
                resetAccountButton.setVisibility(VISIBLE);
                resetAccountText.setVisibility(VISIBLE);
                AndroidUtilities.hideKeyboard(codeField);
                return;
            }
            resetAccountButton.setVisibility(GONE);
            resetAccountText.setVisibility(GONE);
            codeField.setText("");
            currentParams = params;
            passwordString = currentParams.getString("password");
            if (passwordString != null) {
                SerializedData data = new SerializedData(Utilities.hexToBytes(passwordString));
                currentPassword = TLRPC.account_Password.TLdeserialize(data, data.readInt32(false), false);
            }

            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            phoneCode = params.getString("code");

            if (currentPassword != null && !TextUtils.isEmpty(currentPassword.hint)) {
                codeField.setHint(LocaleController.formatString("LoginPasswordHint", R.string.LoginPasswordHint, currentPassword.hint));
            } else {
                codeField.setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
            }
        }

        private void onPasscodeError(boolean clear) {
            if (getParentActivity() == null) {
                return;
            }
            if (clear) {
                codeField.setText("");
            }
            onFieldError(confirmTextView);
        }

        @Override
        public void onNextPressed(String code) {
            Log.d(TAG, "onNextPressed() in PasswordView");
            if (nextPressed) {
                return;
            }

            String oldPassword = codeField.getText().toString();
            if (oldPassword.length() == 0) {
                onPasscodeError(false);
                return;
            }
            nextPressed = true;
            needShowProgress(0);

            Utilities.globalQueue.postRunnable(() -> {
                final byte[] x_bytes;

                TLRPC.PasswordKdfAlgo current_algo = currentPassword.current_algo;
                if (current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                    byte[] passwordBytes = AndroidUtilities.getStringBytes(oldPassword);
                    TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) current_algo;
                    x_bytes = SRPHelper.getX(passwordBytes, algo);
                } else {
                    x_bytes = null;
                }


                final TLRPC.TL_auth_checkPassword req = new TLRPC.TL_auth_checkPassword();

                RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    nextPressed = false;
                    if (error != null && "SRP_ID_INVALID".equals(error.text)) {
                        TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                            if (error2 == null) {
                                currentPassword = (TLRPC.account_Password) response2;
                                onNextPressed(null);
                            }
                        }), ConnectionsManager.RequestFlagWithoutLogin);
                        return;
                    }

                    if (response instanceof TLRPC.TL_auth_authorization) {
                        showDoneButton(false, true);
                        analyticsCollector.onAppEvent(AppEvent.LOGIN_PASSWORD_INSERTED);
                        postDelayed(() -> {
                            needHideProgress(false, false);
                            AndroidUtilities.hideKeyboard(codeField);
                            onAuthSuccess((TLRPC.TL_auth_authorization) response);
                        }, 150);
                    } else {
                        needHideProgress(false);
                        if (error != null && error.text != null) Log.w(TAG, error.text);
                        if (error.text.equals("PASSWORD_HASH_INVALID")) {
                            onPasscodeError(true);
                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                            int time = Utilities.parseInt(error.text);
                            String timeString;
                            if (time < 60) {
                                timeString = LocaleController.formatPluralString("Seconds", time);
                            } else {
                                timeString = LocaleController.formatPluralString("Minutes", time / 60);
                            }
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                        } else {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                        }
                    }
                });

                if (current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                    req.password = SRPHelper.startCheck(x_bytes, currentPassword.srp_id, currentPassword.srp_B, (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) current_algo);
                    if (req.password == null) {
                        TLRPC.TL_error error = new TLRPC.TL_error();
                        error.text = "PASSWORD_HASH_INVALID";
                        requestDelegate.run(null, error);
                        return;
                    }
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                }
            });
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public boolean onBackPressed(boolean force) {
            nextPressed = false;
            needHideProgress(true);
            currentParams = null;
            return true;
        }

        @Override
        public void onShow() {
            super.onShow();
            AndroidUtilities.runOnUIThread(() -> {
                if (codeField != null) {
                    codeField.requestFocus();
                    codeField.setSelection(codeField.length());
                    AndroidUtilities.showKeyboard(codeField);
                }
            }, 100);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeField.getText().toString();
            if (code.length() != 0) {
                bundle.putString("passview_code", code);
            }
            if (currentParams != null) {
                bundle.putBundle("passview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("passview_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }
            String code = bundle.getString("passview_code");
            if (code != null) {
                codeField.setText(code);
            }
        }
    }

    public class LoginActivityResetWaitView extends SlideView {

        private TextView confirmTextView;
        private TextView resetAccountButton;
        private TextView resetAccountTime;
        private TextView resetAccountText;
        private Runnable timeRunnable;

        private Bundle currentParams;
        private String requestPhone;
        private String phoneHash;
        private String phoneCode;
        private int startTime;
        private int waitTime;

        public LoginActivityResetWaitView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            confirmTextView.setTextColor(getContext().getColor(R.color.white_alpha_90));
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

            resetAccountText = new TextView(context);
            resetAccountText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            resetAccountText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            resetAccountText.setText(LocaleController.getString("ResetAccountStatus", R.string.ResetAccountStatus));
            resetAccountText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            resetAccountText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(resetAccountText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 24, 0, 0));

            resetAccountTime = new TextView(context);
            resetAccountTime.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            resetAccountTime.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            resetAccountTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            resetAccountTime.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(resetAccountTime, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 2, 0, 0));

            resetAccountButton = new TextView(context);
            resetAccountButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            resetAccountButton.setText(LocaleController.getString("ResetAccountButton", R.string.ResetAccountButton));
            resetAccountButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            resetAccountButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            resetAccountButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            resetAccountButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            addView(resetAccountButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 7, 0, 0));
            resetAccountButton.setOnClickListener(view -> {
                if (doneProgressView.getTag() != null) {
                    return;
                }
                if (Math.abs(ConnectionsManager.getInstance(currentAccount).getCurrentTime() - startTime) < waitTime) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(LocaleController.getString("ResetMyAccountWarningText", R.string.ResetMyAccountWarningText));
                builder.setTitle(LocaleController.getString("ResetMyAccountWarning", R.string.ResetMyAccountWarning));
                builder.setPositiveButton(LocaleController.getString("ResetMyAccountWarningReset", R.string.ResetMyAccountWarningReset), (dialogInterface, i) -> {
                    needShowProgress(0);
                    TLRPC.TL_account_deleteAccount req = new TLRPC.TL_account_deleteAccount();
                    req.reason = "Forgot password";
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        needHideProgress(false);
                        if (error == null) {
                            Bundle params = new Bundle();
                            params.putString("phoneFormated", requestPhone);
                            params.putString("phoneHash", phoneHash);
                            params.putString("code", phoneCode);
                            setPage(VIEW_REGISTER, true, params, false);
                        } else {
                            if (error.text.equals("2FA_RECENT_CONFIRM")) {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ResetAccountCancelledAlert", R.string.ResetAccountCancelledAlert));
                            } else {
                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                            }
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            });
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("ResetAccount", R.string.ResetAccount);
        }

        private void updateTimeText() {
            int timeLeft = Math.max(0, waitTime - (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - startTime));
            int days = timeLeft / 86400;
            int hours = (timeLeft - days * 86400) / 3600;
            int minutes = (timeLeft - days * 86400 - hours * 3600) / 60;
            int seconds = timeLeft % 60;
            if (days != 0) {
                resetAccountTime.setText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("DaysBold", days) + " " + LocaleController.formatPluralString("HoursBold", hours) + " " + LocaleController.formatPluralString("MinutesBold", minutes)));
            } else {
                resetAccountTime.setText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("HoursBold", hours) + " " + LocaleController.formatPluralString("MinutesBold", minutes) + " " + LocaleController.formatPluralString("SecondsBold", seconds)));
            }
            if (timeLeft > 0) {
                resetAccountButton.setTag(Theme.key_windowBackgroundWhiteGrayText6);
                resetAccountButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            } else {
                resetAccountButton.setTag(Theme.key_windowBackgroundWhite);
                resetAccountButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            currentParams = params;
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            phoneCode = params.getString("code");
            startTime = params.getInt("startTime");
            waitTime = params.getInt("waitTime");
            confirmTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ResetAccountInfo", R.string.ResetAccountInfo, LocaleController.addNbsp(PhoneFormat.getInstance().format("+" + requestPhone)))));
            updateTimeText();
            timeRunnable = new Runnable() {
                @Override
                public void run() {
                    if (timeRunnable != this) {
                        return;
                    }
                    updateTimeText();
                    AndroidUtilities.runOnUIThread(timeRunnable, 1000);
                }
            };
            AndroidUtilities.runOnUIThread(timeRunnable, 1000);
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public boolean onBackPressed(boolean force) {
            needHideProgress(true);
            AndroidUtilities.cancelRunOnUIThread(timeRunnable);
            timeRunnable = null;
            currentParams = null;
            return true;
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            if (currentParams != null) {
                bundle.putBundle("resetview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("resetview_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }
        }
    }

    public class LoginActivityRecoverView extends SlideView {

        private EditTextBoldCursor codeField;
        private TextView confirmTextView;
        private TextView cancelButton;

        private Bundle currentParams;
        private String passwordString;
        private boolean nextPressed;

        //sbdv info
        //окно - Если забыли пароль при 2ФА - введите пароль отправленный на почту
        public LoginActivityRecoverView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            confirmTextView.setText(LocaleController.getString("RestoreEmailSentInfo", R.string.RestoreEmailSentInfo));
            confirmTextView.setTextColor(getContext().getColor(R.color.white_alpha_90));
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getPhoneLoginButtonTitle());
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

            codeField = new EditTextBoldCursor(context);
            codeField.setCursorSize(AndroidUtilities.dp(TelegramDimensions.getPhoneLoginEditTextButtonTextSize()));
            codeField.setCursorWidth(1.5f);
            codeField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            codeField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            codeField.setHint(LocaleController.getString("PasswordCode", R.string.PasswordCode));
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            codeField.setMaxLines(1);
            codeField.setPadding(0, 0, 0, 0);
            codeField.setInputType(InputType.TYPE_CLASS_PHONE);
            codeField.setTransformationMethod(PasswordTransformationMethod.getInstance());
            codeField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            codeField.setTextColor(getContext().getColor(R.color.white_alpha_90));
            codeField.setCursorColor(getContext().getColor(R.color.white_alpha_90));
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getPhoneLoginEditTextButtonTextSize());
            addView(codeField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_HORIZONTAL, 0, 30, 0, 0));
            codeField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });

            cancelButton = new TextView(context);
            cancelButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
            cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            cancelButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            cancelButton.setTextColor(Theme.getColor(Theme.key_chats_actionBackground));
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getPhoneLoginButtonTitle());
            addView(cancelButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 20, 0, 14));
            cancelButton.setOnClickListener(view -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(LocaleController.getString("RestoreEmailTroubleText", R.string.RestoreEmailTroubleText));
                builder.setTitle(LocaleController.getString("RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> setPage(VIEW_PASSWORD, true, new Bundle(), true));
                Dialog dialog = showDialog(builder.create());
                if (dialog != null) {
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setCancelable(false);
                }
            });
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("LoginPassword", R.string.LoginPassword);
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            codeField.setText("");
            currentParams = params;

            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TelegramDimensions.getPhoneLoginButtonTitle());

            String email_unconfirmed_pattern = currentParams.getString("email_unconfirmed_pattern");
            cancelButton.setText(LocaleController.formatString("RestoreEmailTrouble", R.string.RestoreEmailTrouble, email_unconfirmed_pattern));

            AndroidUtilities.showKeyboard(codeField);
            codeField.requestFocus();
        }

        private void onPasscodeError(boolean clear) {
            if (getParentActivity() == null) {
                return;
            }
            if (clear) {
                codeField.setText("");
            }
            onFieldError(confirmTextView);
        }

        @Override
        public void onNextPressed(String code) {
            Log.d(TAG, "onNextPressed() in RecoverView");
            if (nextPressed) {
                return;
            }

            code = codeField.getText().toString();
            if (code.length() == 0) {
                onPasscodeError(false);
                return;
            }
            nextPressed = true;
            needShowProgress(0);
            TLRPC.TL_auth_checkRecoveryPassword req = new TLRPC.TL_auth_checkRecoveryPassword();
            req.code = code;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                needHideProgress(false);
                nextPressed = false;
                if (response instanceof TLRPC.TL_boolTrue) {
                    Bundle params = new Bundle();
                    params.putString("emailCode", req.code);
                    params.putString("password", passwordString);
                    setPage(VIEW_NEW_PASSWORD_STAGE_1, true, params, false);
                } else {
                    if (error != null && error.text != null) Log.w(TAG, error.text);
                    if (error == null || error.text.startsWith("CODE_INVALID")) {
                        onPasscodeError(true);
                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                        int time = Utilities.parseInt(error.text);
                        String timeString;
                        if (time < 60) {
                            timeString = LocaleController.formatPluralString("Seconds", time);
                        } else {
                            timeString = LocaleController.formatPluralString("Minutes", time / 60);
                        }
                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                    } else {
                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
        }

        @Override
        public boolean onBackPressed(boolean force) {
            needHideProgress(true);
            currentParams = null;
            nextPressed = false;
            return true;
        }

        @Override
        public void onShow() {
            super.onShow();
            AndroidUtilities.runOnUIThread(() -> {
                if (codeField != null) {
                    codeField.requestFocus();
                    codeField.setSelection(codeField.length());
                }
            }, 100);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeField.getText().toString();
            if (code != null && code.length() != 0) {
                bundle.putString("recoveryview_code", code);
            }
            if (currentParams != null) {
                bundle.putBundle("recoveryview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("recoveryview_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }
            String code = bundle.getString("recoveryview_code");
            if (code != null) {
                codeField.setText(code);
            }
        }
    }

    public class LoginActivityNewPasswordView extends SlideView {

        private EditTextBoldCursor[] codeField;
        private TextView confirmTextView;
        private TextView cancelButton;

        private String emailCode;
        private String newPassword;
        private String passwordString;
        private TLRPC.account_Password currentPassword;
        private Bundle currentParams;
        private boolean nextPressed;
        private int currentStage;

        public LoginActivityNewPasswordView(Context context, int stage) {
            super(context);
            currentStage = stage;

            setOrientation(VERTICAL);

            codeField = new EditTextBoldCursor[stage == 1 ? 1 : 2];

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

            for (int a = 0; a < codeField.length; a++) {
                codeField[a] = new EditTextBoldCursor(context);
                codeField[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                codeField[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                codeField[a].setCursorSize(AndroidUtilities.dp(20));
                codeField[a].setCursorWidth(1.5f);
                codeField[a].setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                codeField[a].setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
                codeField[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                codeField[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                codeField[a].setMaxLines(1);
                codeField[a].setPadding(0, 0, 0, 0);
                if (stage == 0) {
                    codeField[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                codeField[a].setTransformationMethod(PasswordTransformationMethod.getInstance());
                codeField[a].setTypeface(Typeface.DEFAULT);
                codeField[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                addView(codeField[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_HORIZONTAL, 0, a == 0 ? 20 : 30, 0, 0));
                int num = a;
                codeField[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (num == 0 && codeField.length == 2) {
                        codeField[1].requestFocus();
                        return true;
                    } else if (i == EditorInfo.IME_ACTION_NEXT) {
                        onNextPressed(null);
                        return true;
                    }
                    return false;
                });

                if (stage == 0) {
                    if (a == 0) {
                        codeField[a].setHint(LocaleController.getString("PleaseEnterNewFirstPasswordHint", R.string.PleaseEnterNewFirstPasswordHint));
                    } else {
                        codeField[a].setHint(LocaleController.getString("PleaseEnterNewSecondPasswordHint", R.string.PleaseEnterNewSecondPasswordHint));
                    }
                } else {
                    codeField[a].setHint(LocaleController.getString("PasswordHintPlaceholder", R.string.PasswordHintPlaceholder));
                }
            }

            if (stage == 0) {
                confirmTextView.setText(LocaleController.getString("PleaseEnterNewFirstPasswordLogin", R.string.PleaseEnterNewFirstPasswordLogin));
            } else {
                confirmTextView.setText(LocaleController.getString("PasswordHintTextLogin", R.string.PasswordHintTextLogin));
            }

            cancelButton = new TextView(context);
            cancelButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
            cancelButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            cancelButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            cancelButton.setText(LocaleController.getString("YourEmailSkip", R.string.YourEmailSkip));
            addView(cancelButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 6, 0, 14));
            cancelButton.setOnClickListener(view -> {
                if (currentStage == 0) {
                    recoverPassword(null, null);
                } else {
                    recoverPassword(newPassword, null);
                }
            });
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("NewPassword", R.string.NewPassword);
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            for (int a = 0; a < codeField.length; a++) {
                codeField[a].setText("");
            }
            currentParams = params;
            emailCode = currentParams.getString("emailCode");
            passwordString = currentParams.getString("password");
            if (passwordString != null) {
                SerializedData data = new SerializedData(Utilities.hexToBytes(passwordString));
                currentPassword = TLRPC.account_Password.TLdeserialize(data, data.readInt32(false), false);
                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
            }
            newPassword = currentParams.getString("new_password");

            AndroidUtilities.showKeyboard(codeField[0]);
            codeField[0].requestFocus();
        }

        private void onPasscodeError(boolean clear, int num) {
            if (getParentActivity() == null) {
                return;
            }
            onFieldError(codeField[num]);
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }

            code = codeField[0].getText().toString();
            if (code.length() == 0) {
                onPasscodeError(false, 0);
                return;
            }
            if (currentStage == 0) {
                if (!code.equals(codeField[1].getText().toString())) {
                    onPasscodeError(false, 1);
                    return;
                }
                Bundle params = new Bundle();
                params.putString("emailCode", emailCode);
                params.putString("new_password", code);
                params.putString("password", passwordString);
                setPage(VIEW_NEW_PASSWORD_STAGE_2, true, params, false);
            } else {
                nextPressed = true;
                needShowProgress(0);
                recoverPassword(newPassword, code);
            }
        }

        private void recoverPassword(String password, String hint) {
            TLRPC.TL_auth_recoverPassword req = new TLRPC.TL_auth_recoverPassword();
            req.code = emailCode;
            if (!TextUtils.isEmpty(password)) {
                req.flags |= 1;
                req.new_settings = new TLRPC.TL_account_passwordInputSettings();
                req.new_settings.flags |= 1;
                req.new_settings.hint = hint != null ? hint : "";
                req.new_settings.new_algo = currentPassword.new_algo;
            }
            Utilities.globalQueue.postRunnable(() -> {
                byte[] newPasswordBytes;
                if (password != null) {
                    newPasswordBytes = AndroidUtilities.getStringBytes(password);
                } else {
                    newPasswordBytes = null;
                }

                RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (error != null && ("SRP_ID_INVALID".equals(error.text) || "NEW_SALT_INVALID".equals(error.text))) {
                        TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                            if (error2 == null) {
                                currentPassword = (TLRPC.account_Password) response2;
                                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                                recoverPassword(password, hint);
                            }
                        }), ConnectionsManager.RequestFlagWithoutLogin);
                        return;
                    }
                    needHideProgress(false);
                    if (response instanceof TLRPC.auth_Authorization) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> onAuthSuccess((TLRPC.TL_auth_authorization) response));
                        if (TextUtils.isEmpty(password)) {
                            builder.setMessage(LocaleController.getString("PasswordReset", R.string.PasswordReset));
                        } else {
                            builder.setMessage(LocaleController.getString("YourPasswordChangedSuccessText", R.string.YourPasswordChangedSuccessText));
                        }
                        builder.setTitle(LocaleController.getString("TwoStepVerificationTitle", R.string.TwoStepVerificationTitle));
                        Dialog dialog = showDialog(builder.create());
                        if (dialog != null) {
                            dialog.setCanceledOnTouchOutside(false);
                            dialog.setCancelable(false);
                        }
                    } else if (error != null) {
                        if (error.text != null) Log.w(TAG, error.text);
                        nextPressed = false;
                        if (error.text.startsWith("FLOOD_WAIT")) {
                            int time = Utilities.parseInt(error.text);
                            String timeString;
                            if (time < 60) {
                                timeString = LocaleController.formatPluralString("Seconds", time);
                            } else {
                                timeString = LocaleController.formatPluralString("Minutes", time / 60);
                            }
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                        } else {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                        }
                    }
                });

                if (currentPassword.new_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                    if (password != null) {
                        TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.new_algo;
                        req.new_settings.new_password_hash = SRPHelper.getVBytes(newPasswordBytes, algo);
                        if (req.new_settings.new_password_hash == null) {
                            TLRPC.TL_error error = new TLRPC.TL_error();
                            error.text = "ALGO_INVALID";
                            requestDelegate.run(null, error);
                        }
                    }
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                } else {
                    TLRPC.TL_error error = new TLRPC.TL_error();
                    error.text = "PASSWORD_HASH_INVALID";
                    requestDelegate.run(null, error);
                }
            });
        }

        @Override
        public boolean onBackPressed(boolean force) {
            needHideProgress(true);
            currentParams = null;
            nextPressed = false;
            return true;
        }

        @Override
        public void onShow() {
            super.onShow();
            AndroidUtilities.runOnUIThread(() -> {
                if (codeField != null) {
                    codeField[0].requestFocus();
                    codeField[0].setSelection(codeField[0].length());
                }
            }, 100);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            if (currentParams != null) {
                bundle.putBundle("recoveryview_params" + currentStage, currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("recoveryview_params" + currentStage);
            if (currentParams != null) {
                setParams(currentParams, true);
            }
        }
    }

    public class LoginActivityRegisterView extends SlideView implements ImageUpdater.ImageUpdaterDelegate {
        private OutlineTextContainerView firstNameOutlineView, lastNameOutlineView;

        private EditTextBoldCursor firstNameField;
        private EditTextBoldCursor lastNameField;
        private BackupImageView avatarImage;
        private AvatarDrawable avatarDrawable;
        private View avatarOverlay;
        private RLottieImageView avatarEditor;
        private RadialProgressView avatarProgressView;
        private AnimatorSet avatarAnimation;
        private TextView descriptionTextView;
        private TextView wrongNumber;
        private TextView privacyView;
        private TextView titleTextView;
        private FrameLayout editTextContainer;
        private String requestPhone;
        private String phoneHash;
        private Bundle currentParams;
        private boolean nextPressed = false;

        private RLottieDrawable cameraDrawable;
        private RLottieDrawable cameraWaitDrawable;
        private boolean isCameraWaitAnimationAllowed = true;

        private ImageUpdater imageUpdater;

        private TLRPC.FileLocation avatar;
        private TLRPC.FileLocation avatarBig;

        private boolean createAfterUpload;

        public class LinkSpan extends ClickableSpan {
            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }

            @Override
            public void onClick(View widget) {
                showTermsOfService(false);
            }
        }

        private void showTermsOfService(boolean needAccept) {
            if (currentTermsOfService == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("TermsOfService", R.string.TermsOfService));

            if (needAccept) {
                builder.setPositiveButton(LocaleController.getString("Accept", R.string.Accept), (dialog, which) -> {
                    currentTermsOfService.popup = false;
                    onNextPressed(null);
                });
                builder.setNegativeButton(LocaleController.getString("Decline", R.string.Decline), (dialog, which) -> {
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                    builder1.setTitle(LocaleController.getString("TermsOfService", R.string.TermsOfService));
                    builder1.setMessage(LocaleController.getString("TosDecline", R.string.TosDecline));
                    builder1.setPositiveButton(LocaleController.getString("SignUp", R.string.SignUp), (dialog1, which1) -> {
                        currentTermsOfService.popup = false;
                        onNextPressed(null);
                    });
                    builder1.setNegativeButton(LocaleController.getString("Decline", R.string.Decline), (dialog12, which12) -> {
                        onBackPressed(true);
                        setPage(VIEW_PHONE_INPUT, true, null, true);
                    });
                    showDialog(builder1.create());
                });
            } else {
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            }

            SpannableStringBuilder text = new SpannableStringBuilder(currentTermsOfService.text);
            MessageObject.addEntitiesToText(text, currentTermsOfService.entities, false, false, false, false);
            builder.setMessage(text);

            showDialog(builder.create());
        }

        public LoginActivityRegisterView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            imageUpdater = new ImageUpdater(false, ImageUpdater.FOR_TYPE_USER, false);
            imageUpdater.setOpenWithFrontfaceCamera(true);
            imageUpdater.setSearchAvailable(false);
            imageUpdater.setUploadAfterSelect(false);
            imageUpdater.parentFragment = LoginActivity.this;
            imageUpdater.setDelegate(this);

            FrameLayout avatarContainer = new FrameLayout(context);
            addView(avatarContainer, LayoutHelper.createLinear(78, 78, Gravity.CENTER_HORIZONTAL));

            avatarDrawable = new AvatarDrawable();

            avatarImage = new BackupImageView(context) {
                @Override
                public void invalidate() {
                    if (avatarOverlay != null) {
                        avatarOverlay.invalidate();
                    }
                    super.invalidate();
                }

                @Override
                public void invalidate(int l, int t, int r, int b) {
                    if (avatarOverlay != null) {
                        avatarOverlay.invalidate();
                    }
                    super.invalidate(l, t, r, b);
                }
            };
            avatarImage.setRoundRadius(AndroidUtilities.dp(64));
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REGISTER);
            avatarDrawable.setInfo(5, null, null);
            avatarImage.setImageDrawable(avatarDrawable);
            avatarContainer.addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0x55000000);

            avatarOverlay = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (avatarImage != null && avatarProgressView.getVisibility() == VISIBLE) {
                        paint.setAlpha((int) (0x55 * avatarImage.getImageReceiver().getCurrentAlpha() * avatarProgressView.getAlpha()));
                        canvas.drawCircle(getMeasuredWidth() / 2.0f, getMeasuredHeight() / 2.0f, getMeasuredWidth() / 2.0f, paint);
                    }
                }
            };
            avatarContainer.addView(avatarOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            avatarOverlay.setOnClickListener(view -> {
                imageUpdater.openMenu(avatar != null, () -> {
                    avatar = null;
                    avatarBig = null;
                    showAvatarProgress(false, true);
                    avatarImage.setImage(null, null, avatarDrawable, null);
                    avatarEditor.setAnimation(cameraDrawable);
                    cameraDrawable.setCurrentFrame(0);
                    isCameraWaitAnimationAllowed = true;
                }, dialog -> {
                    if (!imageUpdater.isUploadingImage()) {
                        avatarEditor.setAnimation(cameraDrawable);
                        cameraDrawable.setCustomEndFrame(86);
                        avatarEditor.setOnAnimationEndListener(() -> isCameraWaitAnimationAllowed = true);
                        avatarEditor.playAnimation();
                    } else {
                        avatarEditor.setAnimation(cameraDrawable);
                        cameraDrawable.setCurrentFrame(0, false);
                        isCameraWaitAnimationAllowed = true;
                    }
                }, 0);
                isCameraWaitAnimationAllowed = false;
                avatarEditor.setAnimation(cameraDrawable);
                cameraDrawable.setCurrentFrame(0);
                cameraDrawable.setCustomEndFrame(43);
                avatarEditor.playAnimation();
            });

            cameraDrawable = new RLottieDrawable(R.raw.camera, String.valueOf(R.raw.camera), AndroidUtilities.dp(70), AndroidUtilities.dp(70), false, null);
            cameraWaitDrawable = new RLottieDrawable(R.raw.camera_wait, String.valueOf(R.raw.camera_wait), AndroidUtilities.dp(70), AndroidUtilities.dp(70), false, null);

            avatarEditor = new RLottieImageView(context) {
                @Override
                public void invalidate(int l, int t, int r, int b) {
                    super.invalidate(l, t, r, b);
                    avatarOverlay.invalidate();
                }

                @Override
                public void invalidate() {
                    super.invalidate();
                    avatarOverlay.invalidate();
                }
            };
            avatarEditor.setScaleType(ImageView.ScaleType.CENTER);
            avatarEditor.setAnimation(cameraDrawable);
            avatarEditor.setEnabled(false);
            avatarEditor.setClickable(false);
            avatarContainer.addView(avatarEditor, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            avatarEditor.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                private long lastRun = System.currentTimeMillis();
                private boolean isAttached;
                private Runnable cameraWaitCallback = () -> {
                    if (isAttached) {
                        if (isCameraWaitAnimationAllowed && System.currentTimeMillis() - lastRun >= 10000) {
                            avatarEditor.setAnimation(cameraWaitDrawable);
                            cameraWaitDrawable.setCurrentFrame(0, false);
                            cameraWaitDrawable.setOnAnimationEndListener(() -> AndroidUtilities.runOnUIThread(() -> {
                                cameraDrawable.setCurrentFrame(0, false);
                                avatarEditor.setAnimation(cameraDrawable);
                            }));
                            avatarEditor.playAnimation();
                            lastRun = System.currentTimeMillis();
                        }

                        avatarEditor.postDelayed(this.cameraWaitCallback, 1000);
                    }
                };

                @Override
                public void onViewAttachedToWindow(View v) {
                    isAttached = true;
                    v.post(cameraWaitCallback);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    isAttached = false;
                    v.removeCallbacks(cameraWaitCallback);
                }
            });

            avatarProgressView = new RadialProgressView(context) {
                @Override
                public void setAlpha(float alpha) {
                    super.setAlpha(alpha);
                    avatarOverlay.invalidate();
                }
            };
            avatarProgressView.setSize(AndroidUtilities.dp(30));
            avatarProgressView.setProgressColor(0xffffffff);
            avatarContainer.addView(avatarProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            showAvatarProgress(false, false);

            titleTextView = new TextView(context);
            titleTextView.setText(LocaleController.getString(R.string.RegistrationProfileInfo));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 12, 8, 0));

            descriptionTextView = new TextView(context);
            descriptionTextView.setText(LocaleController.getString("RegisterText2", R.string.RegisterText2));
            descriptionTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            descriptionTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 6, 8, 0));

            editTextContainer = new FrameLayout(context);
            addView(editTextContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 21, 8, 0));

            firstNameOutlineView = new OutlineTextContainerView(context);
            firstNameOutlineView.setText(LocaleController.getString(R.string.FirstName));

            firstNameField = new EditTextBoldCursor(context);
            firstNameField.setCursorSize(AndroidUtilities.dp(20));
            firstNameField.setCursorWidth(1.5f);
            firstNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            firstNameField.setMaxLines(1);
            firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            firstNameField.setOnFocusChangeListener((v, hasFocus) -> firstNameOutlineView.animateSelection(hasFocus ? 1f : 0f));
            firstNameField.setBackground(null);
            firstNameField.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

            firstNameOutlineView.attachEditText(firstNameField);
            firstNameOutlineView.addView(firstNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

            firstNameField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    lastNameField.requestFocus();
                    return true;
                }
                return false;
            });

            lastNameOutlineView = new OutlineTextContainerView(context);
            lastNameOutlineView.setText(LocaleController.getString(R.string.LastName));

            lastNameField = new EditTextBoldCursor(context);
            lastNameField.setCursorSize(AndroidUtilities.dp(20));
            lastNameField.setCursorWidth(1.5f);
            lastNameField.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            lastNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            lastNameField.setMaxLines(1);
            lastNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            lastNameField.setOnFocusChangeListener((v, hasFocus) -> lastNameOutlineView.animateSelection(hasFocus ? 1f : 0f));
            lastNameField.setBackground(null);
            lastNameField.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

            lastNameOutlineView.attachEditText(lastNameField);
            lastNameOutlineView.addView(lastNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

            lastNameField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_DONE || i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });
            buildEditTextLayout(AndroidUtilities.isSmallScreen());

            wrongNumber = new TextView(context);
            wrongNumber.setText(LocaleController.getString("CancelRegistration", R.string.CancelRegistration));
            wrongNumber.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
            wrongNumber.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            wrongNumber.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            wrongNumber.setPadding(0, AndroidUtilities.dp(24), 0, 0);
            wrongNumber.setVisibility(GONE);
            addView(wrongNumber, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 20, 0, 0));
            wrongNumber.setOnClickListener(view -> {
                onBackPressed(false);
            });

            FrameLayout privacyLayout = new FrameLayout(context);
            addView(privacyLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.BOTTOM));

            privacyView = new TextView(context);
            privacyView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
            privacyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, AndroidUtilities.isSmallScreen() ? 13 : 14);
            privacyView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            privacyView.setGravity(Gravity.CENTER_VERTICAL);
            privacyLayout.addView(privacyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? 56 : 60, Gravity.LEFT | Gravity.BOTTOM, 14, 0, 70, 32));
            VerticalPositionAutoAnimator.attach(privacyView);

            String str = LocaleController.getString("TermsOfServiceLogin", R.string.TermsOfServiceLogin);
            SpannableStringBuilder text = new SpannableStringBuilder(str);
            int index1 = str.indexOf('*');
            int index2 = str.lastIndexOf('*');
            if (index1 != -1 && index2 != -1 && index1 != index2) {
                text.replace(index2, index2 + 1, "");
                text.replace(index1, index1 + 1, "");
                text.setSpan(new LoginActivity.LoginActivityRegisterView.LinkSpan(), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            privacyView.setText(text);
        }

        @Override
        public void updateColors() {
            avatarDrawable.invalidateSelf();
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            descriptionTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            firstNameField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            firstNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
            lastNameField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            lastNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
            wrongNumber.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            privacyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            privacyView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));

            firstNameOutlineView.updateColor();
            lastNameOutlineView.updateColor();
        }

        private void buildEditTextLayout(boolean small) {
            boolean firstHasFocus = firstNameField.hasFocus(), lastHasFocus = lastNameField.hasFocus();
            editTextContainer.removeAllViews();

            if (small) {
                LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(HORIZONTAL);

                firstNameOutlineView.setText(LocaleController.getString(R.string.FirstNameSmall));
                lastNameOutlineView.setText(LocaleController.getString(R.string.LastNameSmall));

                linearLayout.addView(firstNameOutlineView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));
                linearLayout.addView(lastNameOutlineView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 8, 0, 0, 0));

                editTextContainer.addView(linearLayout);

                if (firstHasFocus) {
                    firstNameField.requestFocus();
                    AndroidUtilities.showKeyboard(firstNameField);
                } else if (lastHasFocus) {
                    lastNameField.requestFocus();
                    AndroidUtilities.showKeyboard(lastNameField);
                }
            } else {
                firstNameOutlineView.setText(LocaleController.getString(R.string.FirstName));
                lastNameOutlineView.setText(LocaleController.getString(R.string.LastName));

                editTextContainer.addView(firstNameOutlineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 8, 0, 8, 0));
                editTextContainer.addView(lastNameOutlineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 8, 82, 8, 0));
            }
        }

        @Override
        public void didUploadPhoto(final TLRPC.InputFile photo, final TLRPC.InputFile video, double videoStartTimestamp, String videoPath, final TLRPC.PhotoSize bigSize, final TLRPC.PhotoSize smallSize, boolean isVideo, TLRPC.VideoSize emojiMarkup) {
            AndroidUtilities.runOnUIThread(() -> {
                avatar = smallSize.location;
                avatarBig = bigSize.location;
                avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, null);
            });
        }

        private void showAvatarProgress(boolean show, boolean animated) {
            if (avatarEditor == null) {
                return;
            }
            if (avatarAnimation != null) {
                avatarAnimation.cancel();
                avatarAnimation = null;
            }
            if (animated) {
                avatarAnimation = new AnimatorSet();
                if (show) {
                    avatarProgressView.setVisibility(View.VISIBLE);

                    avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f));
                } else {
                    avatarEditor.setVisibility(View.VISIBLE);

                    avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 1.0f),
                            ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f));
                }
                avatarAnimation.setDuration(180);
                avatarAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (avatarAnimation == null || avatarEditor == null) {
                            return;
                        }
                        if (show) {
                            avatarEditor.setVisibility(View.INVISIBLE);
                        } else {
                            avatarProgressView.setVisibility(View.INVISIBLE);
                        }
                        avatarAnimation = null;
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        avatarAnimation = null;
                    }
                });
                avatarAnimation.start();
            } else {
                if (show) {
                    avatarEditor.setAlpha(1.0f);
                    avatarEditor.setVisibility(View.INVISIBLE);
                    avatarProgressView.setAlpha(1.0f);
                    avatarProgressView.setVisibility(View.VISIBLE);
                } else {
                    avatarEditor.setAlpha(1.0f);
                    avatarEditor.setVisibility(View.VISIBLE);
                    avatarProgressView.setAlpha(0.0f);
                    avatarProgressView.setVisibility(View.INVISIBLE);
                }
            }
        }

        @Override
        public boolean onBackPressed(boolean force) {
            if (!force) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.Warning));
                builder.setMessage(LocaleController.getString("AreYouSureRegistration", R.string.AreYouSureRegistration));
                builder.setNegativeButton(LocaleController.getString("Stop", R.string.Stop), (dialogInterface, i) -> {
                    onBackPressed(true);
                    setPage(VIEW_PHONE_INPUT, true, null, true);
                    hidePrivacyView();
                });
                builder.setPositiveButton(LocaleController.getString("Continue", R.string.Continue), null);
                showDialog(builder.create());
                return false;
            }
            needHideProgress(true);
            nextPressed = false;
            currentParams = null;
            return true;
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("YourName", R.string.YourName);
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onShow() {
            super.onShow();
            if (privacyView != null) {
                if (restoringState) {
                    privacyView.setAlpha(1f);
                } else {
                    privacyView.setAlpha(0f);
                    privacyView.animate().alpha(1f).setDuration(200).setStartDelay(300).setInterpolator(AndroidUtilities.decelerateInterpolator).start();
                }
            }
            if (firstNameField != null) {
                firstNameField.requestFocus();
                firstNameField.setSelection(firstNameField.length());
                AndroidUtilities.showKeyboard(firstNameField);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (firstNameField != null) {
                    firstNameField.requestFocus();
                    firstNameField.setSelection(firstNameField.length());
                    AndroidUtilities.showKeyboard(firstNameField);
                }
            }, SHOW_DELAY);
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            firstNameField.setText("");
            lastNameField.setText("");
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            currentParams = params;
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }
            if (currentTermsOfService != null && currentTermsOfService.popup) {
                showTermsOfService(true);
                return;
            }
            if (firstNameField.length() == 0) {
                onFieldError(firstNameOutlineView);
                return;
            }
            nextPressed = true;
            TLRPC.TL_auth_signUp req = new TLRPC.TL_auth_signUp();
            req.phone_code_hash = phoneHash;
            req.phone_number = requestPhone;
            req.first_name = firstNameField.getText().toString();
            req.last_name = lastNameField.getText().toString();
            needShowProgress(0);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (response instanceof TLRPC.TL_auth_authorization) {
                    hidePrivacyView();
                    showDoneButton(false, true);
                    postDelayed(() -> {
                        needHideProgress(false, false);
                        AndroidUtilities.hideKeyboard(fragmentView.findFocus());
                        onAuthSuccess((TLRPC.TL_auth_authorization) response, true);
                        if (avatarBig != null) {
                            TLRPC.FileLocation avatar = avatarBig;
                            Utilities.cacheClearQueue.postRunnable(() -> MessagesController.getInstance(currentAccount).uploadAndApplyUserAvatar(avatar));
                        }
                    }, 150);
                } else {
                    needHideProgress(false);
                    if (error.text.contains("PHONE_NUMBER_INVALID")) {
                        needShowAlert(LocaleController.getString(R.string.RestorePasswordNoEmailTitle), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                    } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                        needShowAlert(LocaleController.getString(R.string.RestorePasswordNoEmailTitle), LocaleController.getString("InvalidCode", R.string.InvalidCode));
                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                        onBackPressed(true);
                        setPage(VIEW_PHONE_INPUT, true, null, true);
                        needShowAlert(LocaleController.getString(R.string.RestorePasswordNoEmailTitle), LocaleController.getString("CodeExpired", R.string.CodeExpired));
                    } else if (error.text.contains("FIRSTNAME_INVALID")) {
                        needShowAlert(LocaleController.getString(R.string.RestorePasswordNoEmailTitle), LocaleController.getString("InvalidFirstName", R.string.InvalidFirstName));
                    } else if (error.text.contains("LASTNAME_INVALID")) {
                        needShowAlert(LocaleController.getString(R.string.RestorePasswordNoEmailTitle), LocaleController.getString("InvalidLastName", R.string.InvalidLastName));
                    } else {
                        needShowAlert(LocaleController.getString(R.string.RestorePasswordNoEmailTitle), error.text);
                    }
                }
            }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String first = firstNameField.getText().toString();
            if (first.length() != 0) {
                bundle.putString("registerview_first", first);
            }
            String last = lastNameField.getText().toString();
            if (last.length() != 0) {
                bundle.putString("registerview_last", last);
            }
            if (currentTermsOfService != null) {
                SerializedData data = new SerializedData(currentTermsOfService.getObjectSize());
                currentTermsOfService.serializeToStream(data);
                String str = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                bundle.putString("terms", str);
                data.cleanup();
            }
            if (currentParams != null) {
                bundle.putBundle("registerview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("registerview_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }

            try {
                String terms = bundle.getString("terms");
                if (terms != null) {
                    byte[] arr = Base64.decode(terms, Base64.DEFAULT);
                    if (arr != null) {
                        SerializedData data = new SerializedData(arr);
                        currentTermsOfService = TLRPC.TL_help_termsOfService.TLdeserialize(data, data.readInt32(false), false);
                        data.cleanup();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            String first = bundle.getString("registerview_first");
            if (first != null) {
                firstNameField.setText(first);
            }
            String last = bundle.getString("registerview_last");
            if (last != null) {
                lastNameField.setText(last);
            }
        }

        private void hidePrivacyView() {
            privacyView.animate().alpha(0f).setDuration(150).setStartDelay(0).setInterpolator(AndroidUtilities.accelerateInterpolator).start();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return null;
    }
}
