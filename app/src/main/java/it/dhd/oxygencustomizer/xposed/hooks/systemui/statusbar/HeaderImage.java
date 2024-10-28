package it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_HEADER_IMAGE_ALPHA;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_HEADER_IMAGE_BOTTOM_FADE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_HEADER_IMAGE_ENABLED;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_HEADER_IMAGE_HEIGHT_PORTRAIT;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_HEADER_IMAGE_LANDSCAPE_ENABLED;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_HEADER_IMAGE_PADDING_SIDE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_HEADER_IMAGE_PADDING_TOP;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_HEADER_IMAGE_TINT;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_HEADER_IMAGE_TINT_CUSTOM;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_HEADER_IMAGE_TINT_INTENSITY;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_HEADER_IMAGE_VALUE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_HEADER_IMAGE_ZOOM_TO_FIT;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.QsHeaderImage.QS_PREFS;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;
import static it.dhd.oxygencustomizer.xposed.hooks.systemui.OpUtils.getPrimaryColor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ImageDecoder;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;

import com.bosphere.fadingedgelayout.FadingEdgeLayout;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.BuildConfig;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.xposed.ResourceManager;
import it.dhd.oxygencustomizer.xposed.XposedMods;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.SettingsLibUtilsProvider;

public class HeaderImage extends XposedMods {

    private static final String listenPackage = Constants.Packages.SYSTEM_UI;
    private final String TAG = "Oxygen Customizer " + this.getClass().getSimpleName() + ": ";

    private final int MAX_TINT_OPACITY = 250;
    ValueAnimator alphaAnimator;
    // QS Header Image
    private FadingEdgeLayout mQsHeaderLayout = null;
    private ImageView mQsHeaderImageView = null;
    private int qshiValue;
    private boolean qshiEnabled;
    private boolean qshiLandscapeEnabled;
    private boolean qshiZoomToFit;
    private int qshiHeightPortrait;
    private int qshiAlpha;
    private int qshiTint;
    private int qshiTintCustom;
    private int qshiPaddingSide;
    private int qshiPaddingTop;
    private final int qshiMinHeight = 50;
    private final int qshiDefaultHeight = 200;
    private int qshiTintIntensity = 50;
    private int mColorAccent;
    private int mColorTextPrimary;
    private int mColorTextPrimaryInverse;
    private int bottomFadeAmount = 0;
    private boolean newControlCenter = false;
    private boolean isFirstExpansionIgnored = true;
    private boolean isResetNeeded = false;
    private final boolean ignore = true;

    public HeaderImage(Context context) {
        super(context);
    }


    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        qshiEnabled = Xprefs.getBoolean(QS_HEADER_IMAGE_ENABLED, false);
        qshiValue = Xprefs.getInt(QS_HEADER_IMAGE_VALUE, -1);
        qshiLandscapeEnabled = Xprefs.getBoolean(QS_HEADER_IMAGE_LANDSCAPE_ENABLED, false);
        qshiAlpha = Xprefs.getSliderInt(QS_HEADER_IMAGE_ALPHA, 255);
        qshiTint = Integer.parseInt(Xprefs.getString(QS_HEADER_IMAGE_TINT, "0"));
        qshiTintCustom = Xprefs.getInt(QS_HEADER_IMAGE_TINT_CUSTOM, 0XFFFFFFFF);
        qshiTintIntensity = Xprefs.getSliderInt(QS_HEADER_IMAGE_TINT_INTENSITY, 50);
        qshiHeightPortrait = Xprefs.getSliderInt(QS_HEADER_IMAGE_HEIGHT_PORTRAIT, qshiDefaultHeight);
        qshiZoomToFit = Xprefs.getBoolean(QS_HEADER_IMAGE_ZOOM_TO_FIT, false);
        qshiPaddingSide = Xprefs.getSliderInt(QS_HEADER_IMAGE_PADDING_SIDE, 0);
        qshiPaddingTop = Xprefs.getSliderInt(QS_HEADER_IMAGE_PADDING_TOP, -16);
        bottomFadeAmount = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                Xprefs.getSliderInt(QS_HEADER_IMAGE_BOTTOM_FADE, 40),
                mContext.getResources().getDisplayMetrics()
        );

        if (Key.length > 0) {
            for (String qsPref : QS_PREFS) {
                if (Key[0].equals(qsPref)) {
                    updateQSHeaderImage();
                }
            }
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Class<?> NewBrightnessSlider;
        try {
            NewBrightnessSlider = findClass("com.oplus.systemui.qs.widget.OplusQsToggleSliderLayout", lpparam.classLoader);
            newControlCenter = true;
            log("New Control Center");
        } catch (Throwable ignored) {
        }

        Class<?> OplusQSContainerImpl;
        try {
            OplusQSContainerImpl = findClass("com.oplus.systemui.qs.OplusQSContainerImpl", lpparam.classLoader);
        } catch (Throwable t) {
            OplusQSContainerImpl = findClass("com.oplusos.systemui.qs.OplusQSContainerImpl", lpparam.classLoader); // OOS 13
        }

        try {
            log("Hooking");
            hookAllMethods(OplusQSContainerImpl, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    log("onFinishInflate");

                    FrameLayout mQuickStatusBarHeader = (FrameLayout) param.thisObject;

                    mQsHeaderLayout = new FadingEdgeLayout(mContext);
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, qshiHeightPortrait, mContext.getResources().getDisplayMetrics()));
                    layoutParams.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, qshiPaddingSide, mContext.getResources().getDisplayMetrics());
                    layoutParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, qshiPaddingSide, mContext.getResources().getDisplayMetrics());
                    layoutParams.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, qshiPaddingTop, mContext.getResources().getDisplayMetrics());

                    mQsHeaderLayout.setLayoutParams(layoutParams);
                    mQsHeaderLayout.setVisibility(View.GONE);

                    mQsHeaderImageView = new ImageView(mContext);
                    mQsHeaderImageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    mQsHeaderLayout.addView(mQsHeaderImageView);

                    mQuickStatusBarHeader.addView(mQsHeaderLayout, 0);

                    updateQSHeaderImage();

                }
            });

            hookAllMethods(OplusQSContainerImpl, "onConfigurationChanged", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Configuration config = (Configuration) param.args[0];
                    if (config.orientation == Configuration.ORIENTATION_LANDSCAPE && !qshiLandscapeEnabled) {
                        mQsHeaderLayout.post(() -> mQsHeaderLayout.setVisibility(View.GONE));
                    } else {
                        mQsHeaderLayout.post(() -> mQsHeaderLayout.setVisibility(View.VISIBLE));
                    }
                }
            });

            hookAllMethods(OplusQSContainerImpl, "updateResources", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    updateQSHeaderImage();
                }
            });

        } catch (Throwable ignored) {
        }

        if (newControlCenter) {
            try {

                /*Class<?> NotificationsQuickSettingsContainer = findClass("com.android.systemui.shade.NotificationsQuickSettingsContainer", lpparam.classLoader);
                hookAllMethods(NotificationsQuickSettingsContainer,
                        "applyBackScaling",
                        new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args[0] instanceof Float) {
                            float f = (float) param.args[0];
                            log("applyBackScaling: " + f);
                        }
                    }
                });

                Class<?> NotificationStackScrollLayout = findClass("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout", lpparam.classLoader);
                hookAllMethods(NotificationStackScrollLayout,
                        "setFractionToShade",
                        new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args[0] instanceof Float) {
                            float height = (float) param.args[0];
                            log("setFractionToShade: " + height);
                        }
                    }
                });*/


                Class<?> NotificationPanelViewControllerExImp = findClass("com.oplus.systemui.shade.NotificationPanelViewControllerExImp", lpparam.classLoader);

                hookAllMethods(NotificationPanelViewControllerExImp, "canScaleFadePanelAtExpandFraction",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                if (!qshiEnabled || mQsHeaderLayout == null) return;
                                if (param.args[0] instanceof Float) {
                                    float expansion = (float) param.args[0];
                                    //log("canScaleFadePanelAtExpandFraction: " + expansion);

                                    if (isFirstExpansionIgnored) {
                                        //log("Ignoring first expansion");
                                        if (expansion >= 0.9f) {
                                            //log("First expansion ignored f>=0.9");
                                            isFirstExpansionIgnored = false;
                                            isResetNeeded = true;
                                        }
                                        if (expansion >= .20f) {
                                            mQsHeaderLayout.setAlpha(1f);
                                            mQsHeaderLayout.setVisibility(View.VISIBLE);
                                        }
                                        return;
                                    }

                                    if (expansion <= .2f) {
                                        //log("Resetting");
                                        isFirstExpansionIgnored = true;
                                        isResetNeeded = false;
                                    }

                                    if (mQsHeaderLayout != null) {
                                        if (expansion <= .900f) {
                                            mQsHeaderLayout.animate()
                                                    .alpha(0f)
                                                    .setDuration(750)
                                                    .setListener(new AnimatorListenerAdapter() {
                                                        @Override
                                                        public void onAnimationEnd(Animator animation) {
                                                            mQsHeaderLayout.setVisibility(View.GONE);
                                                        }
                                                    });
                                        } else {
                                            mQsHeaderLayout.animate()
                                                    .alpha(1f)
                                                    .setDuration(150)
                                                    .setListener(new AnimatorListenerAdapter() {
                                                        @Override
                                                        public void onAnimationEnd(Animator animation) {
                                                            mQsHeaderLayout.setVisibility(View.VISIBLE);
                                                        }
                                                    });
                                        }
                                    }
                                }
                            }
                        });

                /*final Class<?> ScrimControllerClass = findClass(SYSTEM_UI + ".statusbar.phone.ScrimController", lpparam.classLoader);

                hookAllMethods(ScrimControllerClass, "updateScrimColor", new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if(!qshiEnabled) return;
                        if (mQsHeaderLayout == null) return;

                        int alphaIndex = param.args[2] instanceof Float ? 2 : 1;
                        if (findField(ScrimControllerClass, "mScrimBehind").get(param.thisObject).equals(param.args[0])) {
                            float qsAlpha = (float) param.args[alphaIndex];
                            boolean nightMode = (mQsHeaderLayout.getContext().getResources().getConfiguration().uiMode
                                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                            if (nightMode && qsAlpha < .18f // Dark Mode Alpha
                                    || !nightMode && qsAlpha < 0.09f // Light Mode Different Alpha
                                ) {
                                mQsHeaderLayout.setAlpha(0f);
                            } else {
                                mQsHeaderLayout.setAlpha(1f);
                            }
                        }
                    }
                });*/

            } catch (Throwable t) {
                log("Error hooking new Control Center " + t.getMessage());
            }
        }

    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }

    private void updateQSHeaderImage() {
        if (mQsHeaderLayout == null || mQsHeaderImageView == null) {
            return;
        }

        if (!qshiEnabled) {
            mQsHeaderLayout.setVisibility(View.GONE);
            return;
        }

        loadImageOrGif(mQsHeaderImageView);

        // Alpha
        mQsHeaderImageView.setImageAlpha(qshiAlpha);

        // Tint
        mColorAccent = getPrimaryColor(mContext);
        mColorTextPrimary = SettingsLibUtilsProvider.getColorAttrDefaultColor(mContext,
                android.R.attr.textColorPrimary);
        mColorTextPrimaryInverse = SettingsLibUtilsProvider.getColorAttrDefaultColor(
                mContext, android.R.attr.textColorPrimaryInverse);


        ViewGroup.MarginLayoutParams qshiParams = (ViewGroup.MarginLayoutParams) mQsHeaderLayout.getLayoutParams();
        //LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, qshiHeightPortrait, mContext.getResources().getDisplayMetrics()));
        qshiParams.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, qshiPaddingSide, mContext.getResources().getDisplayMetrics());
        qshiParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, qshiPaddingSide, mContext.getResources().getDisplayMetrics());
        qshiParams.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, qshiPaddingTop, mContext.getResources().getDisplayMetrics());
        qshiParams.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, qshiHeightPortrait, mContext.getResources().getDisplayMetrics());
        mQsHeaderLayout.setLayoutParams(qshiParams);
        mQsHeaderLayout.requestLayout();

        Configuration config = mContext.getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE && !qshiLandscapeEnabled) {
            mQsHeaderLayout.setVisibility(View.GONE);
        } else {
            mQsHeaderLayout.setVisibility(View.VISIBLE);
        }

        mQsHeaderLayout.setFadeEdges(false, false, bottomFadeAmount != 0, false);
        mQsHeaderLayout.setFadeSizes(0, 0, bottomFadeAmount, 0);

    }

    private void loadImageOrGif(ImageView iv) {
        AtomicBoolean applyTint = new AtomicBoolean(false);
        int tintColor;
        if (qshiTint == 0) {
            tintColor = -1;
            mQsHeaderImageView.setColorFilter(null);
        } else if (qshiTint == 1) {
            tintColor = mColorAccent;
        } else if (qshiTint == 2) {
            tintColor = mColorTextPrimary;
        } else if (qshiTint == 3) {
            tintColor = mColorTextPrimaryInverse;
        } else if (qshiTint == 4) {
            // validate color and limit custom tint opacity to MAX_TINT_OPACITY
            tintColor = getValidCustomTint(qshiTintCustom);
        } else {
            tintColor = -1;
        }
        if (qshiValue != -1) {
            @SuppressLint("DiscouragedApi") int resId = ResourceManager.modRes.getIdentifier("qs_header_image_" + qshiValue, "drawable", BuildConfig.APPLICATION_ID);
            Drawable drw = ResourcesCompat.getDrawable(ResourceManager.modRes,
                    resId,
                    mContext.getTheme());
            iv.post(() -> loadImageMain(iv, drw));
            applyTint.set(true);
        } else {
            try {
                ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
                executor.scheduleWithFixedDelay(() -> {
                    File Android = new File(Environment.getExternalStorageDirectory() + "/Android");

                    if (Android.isDirectory()) {
                        try {
                            ImageDecoder.Source source = ImageDecoder.createSource(new File(Environment.getExternalStorageDirectory() + "/.oxygen_customizer/header_image.png"));

                            Drawable drawable = ImageDecoder.decodeDrawable(source);
                            iv.post(() -> loadImageMain(iv, drawable));

                            if (drawable instanceof AnimatedImageDrawable) {
                                if (tintColor != -1) {
                                    int fadeFilter = ColorUtils.blendARGB(Color.TRANSPARENT, tintColor, qshiTintIntensity / 100f);
                                    ColorFilter colorFilter = new PorterDuffColorFilter(fadeFilter, PorterDuff.Mode.SRC_ATOP);
                                    drawable.setColorFilter(colorFilter);
                                } else {
                                    drawable.setColorFilter(null);
                                }
                                ((AnimatedImageDrawable) drawable).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                                ((AnimatedImageDrawable) drawable).start();
                                applyTint.set(false);
                            } else {
                                applyTint.set(true);
                            }
                        } catch (Throwable ignored) {
                        }

                        executor.shutdown();
                        executor.shutdownNow();
                    }
                }, 0, 5, TimeUnit.SECONDS);

            } catch (Throwable ignored) {
            }
            if (applyTint.get()) {
                if (tintColor != -1) {
                    int fadeFilter = ColorUtils.blendARGB(Color.TRANSPARENT, tintColor, qshiTintIntensity / 100f);
                    mQsHeaderImageView.setColorFilter(fadeFilter, PorterDuff.Mode.SRC_ATOP);
                }
            } else {
                mQsHeaderImageView.setColorFilter(null);
            }
        }
        if (!qshiZoomToFit) {
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
        } else {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setAdjustViewBounds(false);
            iv.setCropToPadding(false);
        }
    }

    private void loadImageMain(ImageView iv, Drawable drawable) {
        iv.setImageDrawable(drawable);
    }

    private int getValidCustomTint(int customTint) {
        int alpha = Color.alpha(customTint);
        int red = Color.red(customTint);
        int blue = Color.blue(customTint);
        int green = Color.green(customTint);

        if (alpha < 0 || red < 0 || red > 255 || blue < 0 || blue > 255
                || green < 0 || green > 255) {
            return -1;
        }

        //limit tint opacity level (alpha <= MAX_TINT_OPACITY)
        if (alpha > MAX_TINT_OPACITY) {
            alpha = MAX_TINT_OPACITY;
            return Color.argb(alpha, red, green, blue);
        } else {
            return customTint;
        }
    }
}
