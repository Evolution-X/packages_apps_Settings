/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.hardwareinfo;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.Formatter;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.LruCache;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.Preference;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.SimpleColorFilter;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.deviceinfo.batteryinfo.BatteryDesignCapacityPreferenceController;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.widget.LayoutPreference;

import org.evolution.settings.fragments.about.GithubAvatarLoader;
import org.evolution.settings.utils.HttpCachePrefs;
import org.evolution.settings.utils.NetworkUtils;
import org.evolution.settings.utils.OtaEntry;
import org.evolution.settings.utils.UrlUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unified controller for the hardware info card layout.
 *
 * Owns three visual sections, all inside preference_hardware_info.xml:
 *   1. Device image    — fetched from Evolution-X / LineageOS image repos
 *   2. Maintainer card — sourced from:
 *        a) Evolution-X OTA JSON (official builds): github → avatar,
 *           maintainer → name, currently_maintained → badge, paypal → link.
 *           Tapping the card when both a GitHub URL and a donate URL are present
 *           shows a chooser dialog; otherwise goes directly to whichever is set.
 *        b) build_maintainer_summary overlay (unofficial builds): name only,
 *           no badge, no avatar, no chevron
 *   3. OEM + specs card — OEM name from OTA JSON falling back to
 *                         ro.product.manufacturer.
 *
 * OTA JSON is resolved per-branch, trying OTA_BRANCHES in order (cnb, then
 * bka) so Android 17 CNB builds prefer the CNB OTA JSON and only fall back
 * to the legacy Android 16 bka branch if the device has no CNB entry yet.
 *
 * All network calls share a single-thread executor and a common LruCache so
 * device images are never fetched twice.
 */
public class HardwareInfoPreferenceController extends BasePreferenceController {

    private static final String TAG = "HardwareInfoPrefCtrl";

    // -------------------------------------------------------------------------
    // URL templates
    // -------------------------------------------------------------------------

    private static final String EVO_IMAGE_URL =
            "https://raw.githubusercontent.com/Evolution-X/www_gitres/main/devices/images/%s.webp";
    private static final String LINEAGE_IMAGE_URL =
            "https://raw.githubusercontent.com/LineageOS/lineage_wiki/main/images/devices/%s.png";
    private static final String EVO_OTA_URL =
            "https://raw.githubusercontent.com/Evolution-X/OTA/%s/builds/%s.json";

    // Branch lookup order for OTA JSON resolution. CNB (Android 17) is tried
    // first; bka (Android 16) is the legacy fallback for devices that don't
    // yet have a published CNB entry.
    private static final String[] OTA_BRANCHES = { "cnb", "bka" };

    // -------------------------------------------------------------------------
    // Disk-cache SharedPreferences name
    // -------------------------------------------------------------------------

    private static final String PREFS_NAME            = "evolution_maintainer_cache";
    private static final int    CACHE_VERSION          = 3;
    private static final String KEY_CACHE_VERSION      = "cache_version";
    private static final String KEY_HAS_ENTRY_PREFIX   = "has_entry_";
    private static final String KEY_MAINTAINER_PREFIX  = "maintainer_";
    private static final String KEY_DONATE_PREFIX      = "donate_";
    private static final String KEY_GITHUB_PREFIX      = "github_";
    private static final String KEY_MAINTAINED_PREFIX  = "maintained_";

    // -------------------------------------------------------------------------
    // Shared bitmap cache  (device image only)
    // -------------------------------------------------------------------------

    private static final LruCache<String, Bitmap> sBitmapCache = new LruCache<String, Bitmap>(10) {
        @Override
        protected int sizeOf(String key, Bitmap value) { return 1; }
    };

    // -------------------------------------------------------------------------
    // Instance state
    // -------------------------------------------------------------------------

    private static final Interpolator EMPHASIZED_DECELERATE =
            new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    private final SharedPreferences mPrefs;
    private final ExecutorService   mExecutor    = Executors.newSingleThreadExecutor();
    private final Handler           mMainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean mFetchStarted;
    private volatile boolean mDestroyed;
    private boolean mSerialRevealed;

    public HardwareInfoPreferenceController(Context context, String key) {
        super(context, key);
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        clearOutdatedCache();
    }

    private void clearOutdatedCache() {
        if (mPrefs.getInt(KEY_CACHE_VERSION, 0) < CACHE_VERSION) {
            mPrefs.edit().clear().putInt(KEY_CACHE_VERSION, CACHE_VERSION).apply();
        }
    }

    private boolean isOfficialBuild() {
        return "Official".equalsIgnoreCase(
                SystemProperties.get("ro.evolution.build.type", ""));
    }

    // -------------------------------------------------------------------------
    // BasePreferenceController
    // -------------------------------------------------------------------------

    @Override
    public int getAvailabilityStatus() {
        String evoDevice = SystemProperties.get("ro.evolution.device", "");
        if (!evoDevice.isEmpty()) return AVAILABLE;
        return SystemProperties.get("ro.product.device", "").isEmpty()
                ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (!(preference instanceof LayoutPreference)) return;

        LayoutPreference layoutPref = (LayoutPreference) preference;
        String codename = SystemProperties.get("ro.product.device", "");
        if (codename.isEmpty()) {
            preference.setVisible(false);
            return;
        }

        // 1. Device image (async)
        loadDeviceImage(layoutPref, codename,
                SystemProperties.get("ro.product.manufacturer", ""));

        // 2. Specs grid (synchronous — local props only)
        populateSpecsCard(layoutPref);
        bindOem(layoutPref, SystemProperties.get("ro.product.manufacturer", ""));

        // 3. Overlay maintainer fallback (synchronous — no network needed)
        showOverlayMaintainerFallback(layoutPref);

        // 4. One async job: OTA JSON → maintainer card + OEM card.
        //    Only runs on Official builds; unofficial builds use overlay fallback only.
        if (!mFetchStarted && !mExecutor.isShutdown() && !mDestroyed) {
            mFetchStarted = true;
            if (isOfficialBuild()) {
                fetchOtaDataAndShow(layoutPref, codename);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Section 1 — Device image
    // -------------------------------------------------------------------------

    private void loadDeviceImage(LayoutPreference layoutPref, String codename, String knownOem) {
        ImageView imageView = layoutPref.findViewById(R.id.device_image);
        if (imageView == null) return;

        // ── Step 1: local overlay drawable (no network needed) ──────────────
        String overlayDrawableName =
                mContext.getString(R.string.config_deviceImageDrawable).trim();
        if (!overlayDrawableName.isEmpty()) {
            int resId = mContext.getResources().getIdentifier(
                    overlayDrawableName, "drawable", mContext.getPackageName());
            if (resId != 0) {
                imageView.setImageResource(resId);
                fadeIn(imageView, false);
                setVisible(layoutPref, R.id.device_image_placeholder, false);
                return;
            }
            Log.w(TAG, "config_deviceImageDrawable set to '" + overlayDrawableName
                    + "' but drawable not found — falling back to network");
        }

        // ── Step 2: memory cache ─────────────────────────────────────────────
        String cacheKey = "device_" + codename;
        Bitmap cached = sBitmapCache.get(cacheKey);
        if (cached != null) {
            setImageLoading(layoutPref, false);
            imageView.setImageBitmap(cached);
            fadeIn(imageView, false);
            setVisible(layoutPref, R.id.device_image_placeholder, false);
            return;
        }

        // ── Step 3: network (EVO → LineageOS) ────────────────────────────────
        if (mExecutor.isShutdown()) return;
        final String evoUrl     = String.format(EVO_IMAGE_URL, codename);
        final String lineageUrl = String.format(LINEAGE_IMAGE_URL, codename);
        final String oem        = knownOem;

        setImageLoading(layoutPref, true);
        mExecutor.execute(() -> {
            Bitmap bmp = NetworkUtils.fetchBitmap(evoUrl);
            if (bmp == null && !oem.isEmpty() && lineageOemMatches(codename, oem)) {
                bmp = NetworkUtils.fetchBitmap(lineageUrl);
            }
            final Bitmap result = bmp;
            mMainHandler.post(() -> {
                setImageLoading(layoutPref, false);
                if (result != null) {
                    sBitmapCache.put(cacheKey, result);
                    imageView.setImageBitmap(result);
                    fadeIn(imageView, false);
                    setVisible(layoutPref, R.id.device_image_placeholder, false);
                }
            });
        });
    }

    /**
     * Shows or hides the expressive shape-morphing loader that covers the device
     * image slot. Layer colors follow the SettingsLib Material palette so the
     * animation tracks light/dark and dynamic color.
     */
    private void setImageLoading(LayoutPreference layoutPref, boolean loading) {
        LottieAnimationView loader = layoutPref.findViewById(R.id.device_image_loading);
        if (loader == null) return;
        setVisible(layoutPref, R.id.device_image_placeholder, !loading);
        if (!loading) {
            loader.cancelAnimation();
            loader.setVisibility(View.GONE);
            return;
        }
        tintLoader(loader, ".primary", "settingslib_materialColorPrimary");
        tintLoader(loader, ".secondaryContainer",
                "settingslib_materialColorSecondaryContainer");
        tintLoader(loader, ".onSecondaryContainer",
                "settingslib_materialColorOnSecondaryContainer");
        loader.setVisibility(View.VISIBLE);
        loader.playAnimation();
    }

    /**
     * SettingsLib colors are not part of Settings' own R class, so resolve them by
     * name from the merged app resources. If one is missing the layer keeps the
     * animation's own color.
     */
    private void tintLoader(LottieAnimationView loader, String layerName, String colorName) {
        int colorRes = mContext.getResources().getIdentifier(
                colorName, "color", mContext.getPackageName());
        if (colorRes == 0) {
            Log.w(TAG, "Color not found for loader tint: " + colorName);
            return;
        }
        loader.addValueCallback(new KeyPath("**", layerName, "**"),
                LottieProperty.COLOR_FILTER,
                new LottieValueCallback<>(new SimpleColorFilter(mContext.getColor(colorRes))));
    }

    // -------------------------------------------------------------------------
    // Section 2 — Specs grid (synchronous)
    // -------------------------------------------------------------------------

    private void populateSpecsCard(LayoutPreference layoutPref) {
        final String headlineText = getModel();
        setText(layoutPref, R.id.model_headline, headlineText);
        setVisible(layoutPref, R.id.model_headline, true);
        bindStatusPill(layoutPref);

        LinearLayout list = layoutPref.findViewById(R.id.specs_list);
        if (list == null) return;
        list.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(list.getContext());

        // Model number (Build.MODEL + MSV suffix). Skipped when it would just
        // repeat the headline, i.e. when the device has no market name.
        final String model =
                com.android.settings.deviceinfo.HardwareInfoPreferenceController.getDeviceModel();
        if (!model.equals(headlineText)) {
            addSpecRow(list, inflater, R.string.model_info, model);
        }
        addSerialRow(list, inflater, getSerial());
        addSpecRow(list, inflater, R.string.hardware_platform_label, getSoc());
        addSpecRow(list, inflater, R.string.total_ram, getTotalRam());
        addOptionalSpecRow(list, inflater, R.string.storage_settings, getStorage());
        addOptionalSpecRow(list, inflater, R.string.display_category_title, getDisplay());
        addOptionalSpecRow(list, inflater, R.string.power_usage_summary_title, getBatteryCapacity());
        final String codename = getCodename();
        addOptionalSpecRow(list, inflater, R.string.hardware_codename, codename);
        addOptionalSpecRow(list, inflater, R.string.hardware_revision, getHardwareRevision());

        // The SKU is often just the codename again; only show it when it adds something.
        // Same property as HardwareSkuPreferenceController / RegulatoryInfo.
        final String sku = SystemProperties.get("ro.boot.hardware.sku", "");
        if (!sku.equalsIgnoreCase(codename)) {
            addOptionalSpecRow(list, inflater, R.string.hardware_sku, sku);
        }
        addOptionalSpecRow(list, inflater, R.string.manufactured_year, getManufacturedYear());

        styleRows(list);

        // The list must not depend on the OEM string being known.
        final View specs = layoutPref.findViewById(R.id.info_card);
        if (specs != null && specs.getVisibility() != View.VISIBLE) {
            specs.setVisibility(View.VISIBLE);
            fadeIn(specs, true);
        }
    }

    /** Hero corner chip: check icon only for Official builds. */
    private void bindStatusPill(LayoutPreference pref) {
        TextView pill = pref.findViewById(R.id.model_status_pill);
        if (pill == null) return;
        final boolean official = isOfficialBuild();
        pill.setText(official ? R.string.build_type_official : R.string.build_type_unofficial);
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(
                official ? R.drawable.ic_model_status_check : 0, 0, 0, 0);
        pill.setVisibility(View.VISIBLE);
    }

    private String getStorage() {
        final StorageManager sm = mContext.getSystemService(StorageManager.class);
        // Marketed size (e.g. 128 GB) rather than the smaller /data partition.
        final long bytes = sm == null ? 0 : sm.getPrimaryStorageSize();
        return bytes > 0 ? Formatter.formatShortFileSize(mContext, bytes) : "";
    }

    private String getDisplay() {
        try {
            DisplayManager dm = mContext.getSystemService(DisplayManager.class);
            Display display = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
            if (display == null) return "";
            int w = 0, h = 0;
            float hz = 0f;
            for (Display.Mode m : display.getSupportedModes()) {
                w = Math.max(w, m.getPhysicalWidth());
                h = Math.max(h, m.getPhysicalHeight());
                hz = Math.max(hz, m.getRefreshRate());
            }
            if (w == 0 || h == 0) return "";
            return mContext.getString(
                    R.string.screen_resolution_refresh_rate_combined_displayed_text,
                    String.valueOf(Math.max(w, h)), String.valueOf(Math.min(w, h)),
                    String.valueOf(Math.round(hz)));
        } catch (RuntimeException e) {
            Log.d(TAG, "No display info available", e);
            return "";
        }
    }

    /** Design capacity; empty when the device reports none or the overlay hides it. */
    private String getBatteryCapacity() {
        if (!mContext.getResources().getBoolean(R.bool.config_show_battery_design_capacity)) {
            return "";
        }
        final int mah = BatteryDesignCapacityPreferenceController.getDesignCapacityMah(mContext);
        return mah > 0 ? mContext.getString(R.string.battery_design_capacity_summary, mah) : "";
    }

    private String getCodename() {
        String c = SystemProperties.get("ro.evolution.device", "");
        return c.isEmpty() ? SystemProperties.get("ro.product.device", "") : c;
    }

    private String getManufacturedYear() {
        try {
            String year = FeatureFactory.getFeatureFactory()
                    .getHardwareInfoFeatureProvider().getManufacturedYear();
            return year == null ? "" : year;
        } catch (RuntimeException e) {
            Log.d(TAG, "No manufactured year available", e);
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // Spec rows: dense label-left / value-right, copy on tap or long-press
    // -------------------------------------------------------------------------

    private View inflateRow(LinearLayout list, LayoutInflater inflater, int labelRes,
            String value) {
        View row = inflater.inflate(R.layout.model_spec_row, list, false);
        ((TextView) row.findViewById(R.id.spec_label)).setText(labelRes);
        ((TextView) row.findViewById(R.id.spec_value)).setText(value);
        list.addView(row);
        return row;
    }

    private void addSpecRow(LinearLayout list, LayoutInflater inflater, int labelRes,
            String value) {
        View row = inflateRow(list, inflater, labelRes, value);
        View content = row.findViewById(R.id.spec_content);

        final boolean copyable = !value.isEmpty()
                && !value.equals(mContext.getString(R.string.device_info_not_available));
        content.setClickable(copyable);
        content.setLongClickable(copyable);
        if (!copyable) return;

        final String label = mContext.getString(labelRes);
        content.setOnClickListener(v -> copyToClipboard(label, value, false));
        content.setOnLongClickListener(v -> {
            copyToClipboard(label, value, false);
            return true;
        });
    }

    /** Adds a row only when [value] is non-empty. */
    private void addOptionalSpecRow(LinearLayout list, LayoutInflater inflater, int labelRes,
            String value) {
        if (value == null || value.isEmpty()) return;
        addSpecRow(list, inflater, labelRes, value);
    }

    /**
     * The serial is shown masked (last four characters only). Tap toggles the full
     * value, long-press copies it. Copies are flagged sensitive so the system
     * clipboard preview hides them.
     */
    private void addSerialRow(LinearLayout list, LayoutInflater inflater, String serial) {
        final boolean available = !serial.isEmpty()
                && !serial.equals(mContext.getString(R.string.device_info_not_available));
        if (!available) {
            addSpecRow(list, inflater, R.string.status_serial_number, serial);
            return;
        }

        View row = inflateRow(list, inflater, R.string.status_serial_number,
                mSerialRevealed ? serial : maskSerial(serial));
        View content = row.findViewById(R.id.spec_content);
        TextView valueView = row.findViewById(R.id.spec_value);
        final String label = mContext.getString(R.string.status_serial_number);

        content.setOnClickListener(v -> {
            mSerialRevealed = !mSerialRevealed;
            valueView.setText(mSerialRevealed ? serial : maskSerial(serial));
        });
        content.setOnLongClickListener(v -> {
            copyToClipboard(label, serial, true);
            return true;
        });
    }

    private static String maskSerial(String serial) {
        final String dots = "\u2022\u2022\u2022\u2022";
        return serial.length() <= 4 ? dots : dots + serial.substring(serial.length() - 4);
    }

    private void copyToClipboard(String label, String text, boolean sensitive) {
        ClipboardManager cm = mContext.getSystemService(ClipboardManager.class);
        if (cm == null) return;
        ClipData clip = ClipData.newPlainText(label, text);
        if (sensitive) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        // The system shows its own clipboard confirmation.
        cm.setPrimaryClip(clip);
    }

    // -------------------------------------------------------------------------
    // Segmented rows: outer corners 28dp (token), inner 4dp, shape morphs on press
    // -------------------------------------------------------------------------

    private void styleRows(LinearLayout list) {
        final int n = list.getChildCount();
        final float outer = dimenPx("settingslib_preference_corner_radius", 28);
        final float inner = dp(4);
        final float pressed = dp(16);
        final int gap = Math.round(dimenPx("settingslib_expressive_space_extrasmall2", 4));
        final int fill = colorByName("settingslib_materialColorSurfaceBright");
        final ColorStateList rippleColor = highlightColor(list);

        for (int i = 0; i < n; i++) {
            final View row = list.getChildAt(i);
            final float top = i == 0 ? outer : inner;
            final float bottom = i == n - 1 ? outer : inner;
            final float[] base = {top, top, top, top, bottom, bottom, bottom, bottom};
            final float[] down = new float[8];
            for (int k = 0; k < 8; k++) down[k] = Math.max(base[k], pressed);

            final GradientDrawable bg = new GradientDrawable();
            bg.setColor(fill);
            bg.setCornerRadii(base);
            row.setBackground(new RippleDrawable(rippleColor, bg, null));
            row.setClipToOutline(true);

            final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
            lp.bottomMargin = i == n - 1 ? 0 : gap;
            row.setLayoutParams(lp);

            if (!row.isClickable()) continue;
            row.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        morphCorners(v, bg, down);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        morphCorners(v, bg, base);
                        break;
                    default:
                        break;
                }
                return false; // clicks and long-presses still fire
            });
        }
    }

    private void morphCorners(View row, GradientDrawable bg, float[] to) {
        final Object running = row.getTag(R.id.spec_label);
        if (running instanceof ValueAnimator) ((ValueAnimator) running).cancel();

        final float[] from = bg.getCornerRadii();
        if (from == null) return;
        final float[] cur = new float[8];
        final ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(250);
        a.setInterpolator(EMPHASIZED_DECELERATE);
        a.addUpdateListener(an -> {
            final float t = (float) an.getAnimatedValue();
            for (int k = 0; k < 8; k++) cur[k] = from[k] + (to[k] - from[k]) * t;
            bg.setCornerRadii(cur);
            row.invalidateOutline();
        });
        row.setTag(R.id.spec_label, a);
        a.start();
    }

    /** M3 emphasized-decelerate fade; surfaces also rise a few dp as they settle. */
    private void fadeIn(View v, boolean rise) {
        if (rise && v.getAlpha() < 1f) v.setTranslationY(dp(12));
        v.animate().alpha(1f).translationY(0f).setDuration(450)
                .setInterpolator(EMPHASIZED_DECELERATE).start();
    }

    private float dp(float v) {
        return v * mContext.getResources().getDisplayMetrics().density;
    }

    /** SettingsLib dimens are not in Settings' R class; resolve by name with a dp fallback. */
    private float dimenPx(String name, int fallbackDp) {
        final int id = mContext.getResources().getIdentifier(
                name, "dimen", mContext.getPackageName());
        return id != 0 ? mContext.getResources().getDimension(id) : dp(fallbackDp);
    }

    private int colorByName(String name) {
        final int id = mContext.getResources().getIdentifier(
                name, "color", mContext.getPackageName());
        if (id == 0) {
            Log.w(TAG, "Color not found: " + name);
            return 0x33808080;
        }
        return mContext.getColor(id);
    }

    private static ColorStateList highlightColor(View anchor) {
        android.content.res.TypedArray ta = anchor.getContext().obtainStyledAttributes(
                new int[]{android.R.attr.colorControlHighlight});
        ColorStateList csl = ta.getColorStateList(0);
        ta.recycle();
        return csl != null ? csl : ColorStateList.valueOf(0x1F808080);
    }
    private void setVisible(LayoutPreference pref, int viewId, boolean visible) {
        View v = pref.findViewById(viewId);
        if (v != null) v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private String getModel() {
        String market = SystemProperties.get("ro.product.marketname", "");
        return market.isEmpty() ? Build.MODEL : market;
    }

    @SuppressWarnings("HardwareIds")
    private String getSerial() {
        try {
            return Build.getSerial();
        } catch (SecurityException e) {
            Log.w(TAG, "No READ_PHONE_STATE for serial", e);
        }
        String s = SystemProperties.get("ro.serialno", "");
        return s.isEmpty() ? mContext.getString(R.string.device_info_not_available) : s;
    }

    private String getSoc() {
        return orNotAvailable(SoCModelPreferenceController.getSocModel());
    }

    private String getTotalRam() {
        return orNotAvailable(TotalRAMPreferenceController.getTotalRam(mContext));
    }

    private String orNotAvailable(String value) {
        return value == null || value.isEmpty()
                ? mContext.getString(R.string.device_info_not_available) : value;
    }

    private String getHardwareRevision() {
        for (String prop : new String[]{
                "ro.boot.hardware.revision", "ro.hardware.revision", "ro.boot.hwrev"}) {
            String v = SystemProperties.get(prop, "");
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    // -------------------------------------------------------------------------
    // Section 3 — Single async OTA fetch: maintainer card + OEM card
    // -------------------------------------------------------------------------

    /**
     * Shows the maintainer card immediately using the build_maintainer_summary
     * overlay string. Will be overwritten if OTA fetch succeeds.
     * Covers the case where network is unavailable or GitHub is blocked.
     */
    private void showOverlayMaintainerFallback(LayoutPreference layoutPref) {
        String summary = UrlUtils.trimToEmpty(mContext.getString(R.string.build_maintainer_summary));
        if (summary.isEmpty() || summary.equalsIgnoreCase("Unknown")) return;

        String donateUrl = UrlUtils.trimToEmpty(
                mContext.getString(R.string.build_maintainer_donate_url));

        showMaintainerCard(layoutPref, null, summary, donateUrl);
    }

    private void fetchOtaDataAndShow(LayoutPreference layoutPref, String codename) {
        mExecutor.execute(() -> {
            if (mDestroyed) return;

            OtaEntry data = null;

            outer:
            for (String branch : OTA_BRANCHES) {
                for (String candidate : getCodenameCandidates(codename)) {
                    data = resolveOtaData(branch, candidate);
                    if (data != null) break outer;
                }
            }

            // OEM fallback for unofficial devices
            final String oemRaw;
            if (data != null && data.oem != null && !data.oem.isEmpty()) {
                oemRaw = data.oem;
            } else {
                String sysProp = SystemProperties.get("ro.product.manufacturer", "");
                oemRaw = sysProp.isEmpty() ? null : sysProp;
            }

            // Unofficial overlay fallback
            final String overlayMaintainer;
            final String overlayDonateUrl;
            if (data == null || data.maintainer == null || data.maintainer.isEmpty()) {
                String summary = UrlUtils.trimToEmpty(mContext.getString(R.string.build_maintainer_summary));
                overlayMaintainer = summary.equalsIgnoreCase("Unknown") ? null : summary;
                overlayDonateUrl  = UrlUtils.trimToEmpty(mContext.getString(R.string.build_maintainer_donate_url));
            } else {
                overlayMaintainer = null;
                overlayDonateUrl  = null;
            }

            final OtaEntry finalData       = data;
            final String   finalOem        = oemRaw;
            final String   finalOverlay    = overlayMaintainer;
            final String   finalOverlayUrl = overlayDonateUrl;

            mMainHandler.post(() -> {
                if (mDestroyed) return;
                showMaintainerCard(layoutPref, finalData, finalOverlay, finalOverlayUrl);
                bindOem(layoutPref, finalOem);
                if (finalOem != null && !finalOem.isEmpty()) {
                    loadDeviceImage(layoutPref, codename, finalOem);
                }
            });
        });
    }

    // ---- Maintainer card ----------------------------------------------------

    private void showMaintainerCard(LayoutPreference layoutPref,
            OtaEntry data, String overlayName, String overlayDonateUrl) {
        View card = layoutPref.findViewById(R.id.maintainer_card);
        if (card == null) return;

        final boolean isOfficial;
        final String displayName;

        if (data != null && data.hasMaintainer()) {
            isOfficial  = true;
            displayName = data.maintainer;
        } else if (overlayName != null && !overlayName.isEmpty()) {
            isOfficial  = false;
            displayName = overlayName;
        } else {
            return;
        }

        TextView  nameView   = layoutPref.findViewById(R.id.maintainer_name);
        ImageView badgeView  = layoutPref.findViewById(R.id.maintainer_verified_badge);
        ImageView chevron    = layoutPref.findViewById(R.id.maintainer_chevron);
        ImageView avatarView = layoutPref.findViewById(R.id.maintainer_avatar);

        if (nameView != null) nameView.setText(maintainedBy(displayName));

        TextView initialView = layoutPref.findViewById(R.id.maintainer_avatar_initial);
        if (initialView != null) {
            initialView.setText(displayName.substring(0,
                    displayName.offsetByCodePoints(0, 1)).toUpperCase());
        }

        if (isOfficial) {
            if (badgeView != null) {
                badgeView.setVisibility(data.currentlyMaintained ? View.VISIBLE : View.GONE);
            }

            final String githubUrl = data.githubUrl();
            final String donateUrl = UrlUtils.sanitizeUrl(data.donateUrl);

            setupChevronAndClick(card, chevron, displayName, githubUrl, donateUrl);

            if (avatarView != null && data.hasGithub()) {
                loadGithubAvatar(layoutPref, data.github);
            }
        } else {
            if (badgeView != null) badgeView.setVisibility(View.GONE);
            final String donateUrl = UrlUtils.sanitizeUrl(overlayDonateUrl);
            setupChevronAndClick(card, chevron, displayName, null, donateUrl);
        }

        card.setVisibility(View.VISIBLE);
        fadeIn(card, true);
    }

    /** "Maintained by <b>Name</b>", reusing the existing maintainer label. */
    private CharSequence maintainedBy(String name) {
        return new SpannableStringBuilder(mContext.getString(R.string.build_maintainer_label))
                .append(' ')
                .append(name, new StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void loadGithubAvatar(LayoutPreference layoutPref, String username) {
        ImageView av = layoutPref.findViewById(R.id.maintainer_avatar);
        if (av == null) return;
        GithubAvatarLoader.getInstance().loadIntoImageView(mContext, av, username);
    }

    /**
     * Shows or hides the chevron and wires up the card click.
     *
     * - Both URLs present → tapping shows a chooser dialog (GitHub / Donate)
     * - Only one URL      → tapping goes directly to that URL
     * - Neither           → card is non-clickable, chevron hidden
     */
    private void setupChevronAndClick(View card, ImageView chevron, String displayName,
            String githubUrl, String donateUrl) {
        boolean hasGithub = githubUrl != null;
        boolean hasDonate = donateUrl != null;
        boolean hasAny    = hasGithub || hasDonate;

        if (chevron != null) {
            chevron.setVisibility(hasAny ? View.VISIBLE : View.GONE);
        }

        if (!hasAny) {
            card.setClickable(false);
            card.setOnClickListener(null);
            return;
        }

        card.setClickable(true);
        card.setFocusable(true);

        if (hasGithub && hasDonate) {
            card.setOnClickListener(v ->
                    showMaintainerLinkDialog(v, displayName, githubUrl, donateUrl));
        } else {
            String only = hasGithub ? githubUrl : donateUrl;
            card.setOnClickListener(v -> openUrl(only));
        }
    }

    /**
     * Shows an AlertDialog letting the user choose between the maintainer's
     * GitHub profile and their donate link.
     */
    private void showMaintainerLinkDialog(View anchorView, String displayName,
            String githubUrl, String donateUrl) {
        android.content.Context ctx = anchorView.getContext();
        while (ctx instanceof android.content.ContextWrapper) {
            if (ctx instanceof android.app.Activity) break;
            ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
        }
        if (!(ctx instanceof android.app.Activity)
                || ((android.app.Activity) ctx).isFinishing()) return;

        CharSequence[] items = {
            mContext.getString(R.string.maintainer_link_github),
            mContext.getString(R.string.maintainer_link_donate),
        };
        new AlertDialog.Builder(ctx)
                .setTitle(displayName)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) openUrl(githubUrl);
                    else            openUrl(donateUrl);
                })
                .show();
    }

    // ---- OEM line -----------------------------------------------------------

    /** Sets the hero's OEM line: local manufacturer first, replaced by the OTA value. */
    private void bindOem(LayoutPreference layoutPref, String oemRaw) {
        TextView oemNameView = layoutPref.findViewById(R.id.oem_name);
        if (oemNameView == null) return;
        final boolean has = oemRaw != null && !oemRaw.isEmpty();
        if (has) oemNameView.setText(oemRaw);
        oemNameView.setVisibility(has ? View.VISIBLE : View.GONE);
    }

    // -------------------------------------------------------------------------
    // OTA JSON resolution  (disk cache + ETag revalidation via HttpCachePrefs)
    // -------------------------------------------------------------------------

    /**
     * Resolves OTA data for a single (branch, codename) pair. Cache keys are
     * namespaced by branch so a cached miss/hit from one branch (e.g. bka)
     * can never shadow a lookup against another branch (e.g. cnb).
     */
    private OtaEntry resolveOtaData(String branch, String codename) {
        String cacheSuffix = branch + "_" + codename;
        HttpCachePrefs cache = new HttpCachePrefs(mPrefs, cacheSuffix);
        boolean hasCached = mPrefs.getBoolean(KEY_HAS_ENTRY_PREFIX + cacheSuffix, false);

        if (hasCached && !cache.isStale()) {
            return readCachedOtaData(cacheSuffix);
        }
        return fetchOtaDataForCodename(branch, codename, cacheSuffix, cache);
    }

    private OtaEntry readCachedOtaData(String cacheSuffix) {
        if (!mPrefs.getBoolean(KEY_HAS_ENTRY_PREFIX + cacheSuffix, false)) return null;
        String maintainer = UrlUtils.trimToEmpty(mPrefs.getString(KEY_MAINTAINER_PREFIX + cacheSuffix, ""));
        return new OtaEntry(
                maintainer.isEmpty() ? null : maintainer,
                mPrefs.getString(KEY_GITHUB_PREFIX      + cacheSuffix, ""),
                mPrefs.getString(KEY_DONATE_PREFIX      + cacheSuffix, ""),
                mPrefs.getString("oem_"                 + cacheSuffix, ""),
                mPrefs.getBoolean(KEY_MAINTAINED_PREFIX + cacheSuffix, false)
        );
    }

    private OtaEntry fetchOtaDataForCodename(String branch, String codename,
            String cacheSuffix, HttpCachePrefs cache) {
        try {
            NetworkUtils.FetchResult r = NetworkUtils.fetchWithStatus(
                    String.format(EVO_OTA_URL, branch, codename),
                    cache.buildHeaders(null));

            if (r.isNotModified()) {
                cache.touchLastCheck();
                return readCachedOtaData(cacheSuffix);
            }

            if (!r.isOk() || r.bytes == null) {
                return readCachedOtaData(cacheSuffix);
            }

            JSONObject root     = new JSONObject(r.bodyAsString());
            JSONArray  response = root.optJSONArray("response");
            if (response == null || response.length() == 0) {
                clearCache(cacheSuffix, cache);
                return null;
            }

            JSONObject entry = response.optJSONObject(0);
            if (entry == null) { clearCache(cacheSuffix, cache); return null; }

            boolean maintained = entry.optBoolean("currently_maintained", false);
            String  maintainer = UrlUtils.trimToEmpty(entry.optString("maintainer", null));
            String  paypal     = UrlUtils.trimToEmpty(entry.optString("paypal",     null));
            String  github     = UrlUtils.trimToEmpty(entry.optString("github",     null));
            String  oem        = UrlUtils.trimToEmpty(entry.optString("oem",        null));

            if (!maintained || maintainer.isEmpty()) {
                clearCache(cacheSuffix, cache);
                if (!oem.isEmpty()) {
                    return new OtaEntry(null, null, null, oem, false);
                }
                return null;
            }

            mPrefs.edit()
                    .putBoolean(KEY_HAS_ENTRY_PREFIX  + cacheSuffix, true)
                    .putBoolean(KEY_MAINTAINED_PREFIX + cacheSuffix, maintained)
                    .putString(KEY_MAINTAINER_PREFIX  + cacheSuffix, maintainer)
                    .putString(KEY_DONATE_PREFIX      + cacheSuffix, paypal)
                    .putString(KEY_GITHUB_PREFIX      + cacheSuffix, github)
                    .putString("oem_"                 + cacheSuffix, oem)
                    .apply();
            cache.write(r.etag, r.lastModified);

            return new OtaEntry(maintainer, github, paypal, oem, maintained);

        } catch (Exception e) {
            Log.d(TAG, "OTA fetch failed for " + branch + "/" + codename, e);
            return readCachedOtaData(cacheSuffix);
        }
    }

    private void clearCache(String cacheSuffix, HttpCachePrefs cache) {
        mPrefs.edit()
                .putBoolean(KEY_HAS_ENTRY_PREFIX  + cacheSuffix, false)
                .remove(KEY_MAINTAINER_PREFIX     + cacheSuffix)
                .remove(KEY_MAINTAINED_PREFIX     + cacheSuffix)
                .remove(KEY_DONATE_PREFIX         + cacheSuffix)
                .remove(KEY_GITHUB_PREFIX         + cacheSuffix)
                .remove("oem_"                    + cacheSuffix)
                .apply();
        cache.invalidate();
    }

    // -------------------------------------------------------------------------
    // Codename candidates
    // -------------------------------------------------------------------------

    private Set<String> getCodenameCandidates(String primary) {
        Set<String> result = new LinkedHashSet<>();
        addIfNotEmpty(result, SystemProperties.get("ro.evolution.device", null));
        addIfNotEmpty(result, primary);
        addIfNotEmpty(result, SystemProperties.get("ro.product.vendor.device", null));
        addIfNotEmpty(result, SystemProperties.get("ro.build.product", null));
        return result;
    }

    private void addIfNotEmpty(Set<String> set, String value) {
        String clean = UrlUtils.trimToEmpty(value);
        if (!clean.isEmpty()) set.add(clean);
    }

    // -------------------------------------------------------------------------
    // Misc helpers
    // -------------------------------------------------------------------------

    private void setText(LayoutPreference pref, int viewId, String text) {
        TextView tv = pref.findViewById(viewId);
        if (tv != null) tv.setText(text);
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "No activity for URL: " + url, e);
        }
    }

    private boolean lineageOemMatches(String codename, String expectedOem) {
        String cacheKey = "lineage_oem_" + codename;
        String cachedVendor = mPrefs.getString(cacheKey, null);

        if (cachedVendor == null) {
            String yamlUrl = "https://raw.githubusercontent.com/LineageOS/lineage_wiki"
                    + "/main/_data/devices/" + codename + ".yml";
            String yaml;
            try {
                yaml = NetworkUtils.fetchString(yamlUrl, null);
            } catch (IOException e) {
                Log.w(TAG, "Failed to fetch LineageOS wiki YAML for " + codename, e);
                return false;
            }
            if (yaml == null || yaml.isEmpty()) return false;

            cachedVendor = "";
            for (String line : yaml.split("\n")) {
                if (line.startsWith("vendor:")) {
                    cachedVendor = line.replace("vendor:", "").trim()
                            .replace("\"", "").replace("'", "");
                    break;
                }
            }
            mPrefs.edit().putString(cacheKey, cachedVendor).apply();
        }

        return !cachedVendor.isEmpty()
                && cachedVendor.equalsIgnoreCase(expectedOem);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void onDestroy() {
        mDestroyed = true;
        if (!mExecutor.isShutdown()) {
            mExecutor.shutdown();
        }
        // Do NOT shut down GithubAvatarLoader — it is a shared singleton.
    }
}
