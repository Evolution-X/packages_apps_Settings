/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.hardwareinfo;

import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
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
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.util.LruCache;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.compose.ui.platform.ComposeView;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.deviceinfo.HardwareInfoPreferenceController;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.android.settings.deviceinfo.hardwareinfo.ModelPageMotion.DEFAULT_SPATIAL_DAMPING;
import static com.android.settings.deviceinfo.hardwareinfo.ModelPageMotion.DEFAULT_SPATIAL_STIFFNESS;
import static com.android.settings.deviceinfo.hardwareinfo.ModelPageMotion.FAST_EFFECTS_DAMPING;
import static com.android.settings.deviceinfo.hardwareinfo.ModelPageMotion.FAST_EFFECTS_STIFFNESS;
import static com.android.settings.deviceinfo.hardwareinfo.ModelPageMotion.FAST_SPATIAL_DAMPING;
import static com.android.settings.deviceinfo.hardwareinfo.ModelPageMotion.FAST_SPATIAL_STIFFNESS;
import static com.android.settings.deviceinfo.hardwareinfo.ModelPageMotion.SLOW_EFFECTS_DAMPING;
import static com.android.settings.deviceinfo.hardwareinfo.ModelPageMotion.SLOW_EFFECTS_STIFFNESS;

/**
 * Unified controller for the hardware info card layout.
 *
 * Owns three visual sections, all inside preference_hardware_info.xml:
 *   1. Device image    — LineageOS wiki (variant aware) > Evolution-X gitres >
 *                        Wikipedia; the image tile is hidden if none is found
 *   2. Maintainer card — sourced from:
 *        a) Evolution-X OTA JSON (official builds): github → avatar,
 *           maintainer → name, currently_maintained → badge, paypal → link.
 *           Tapping the card opens the GitHub profile; the Support button
 *           opens the donate URL (hidden when none is set).
 *        b) build_maintainer_summary overlay (unofficial builds): name and
 *           Support button only, no badge, no avatar, card not clickable
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
public class ModelPageController extends BasePreferenceController {

    private static final String TAG = "HardwareInfoPrefCtrl";

    // -------------------------------------------------------------------------
    // URL templates
    // -------------------------------------------------------------------------

    private static final String EVO_IMAGE_URL =
            "https://raw.githubusercontent.com/Evolution-X/www_gitres/main/devices/images/%s.webp";
    private static final String LINEAGE_IMAGE_URL =
            "https://raw.githubusercontent.com/LineageOS/lineage_wiki/main/images/devices/%s";
    private static final String LINEAGE_DEVICE_YML_URL =
            "https://raw.githubusercontent.com/LineageOS/lineage_wiki/main/_data/devices/%s.yml";
    private static final String WIKIPEDIA_SUMMARY_URL =
            "https://en.wikipedia.org/api/rest_v1/page/summary/%s";
    private static final String WIKIPEDIA_SEARCH_URL =
            "https://en.wikipedia.org/w/api.php?action=query&format=json&list=search"
            + "&srlimit=1&srnamespace=0&srsearch=%s";
    // Lineage wiki names variant files <codename>_variant1 .. <codename>_variantN.
    private static final int LINEAGE_MAX_VARIANTS = 8;
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

    // Font scale at which spec values may wrap to two lines.
    private static final float LARGE_FONT_SCALE = 1.3f;
    private static final int MAX_IMAGE_ATTEMPTS = 3;

    private final SharedPreferences mPrefs;
    private final ExecutorService   mExecutor    = Executors.newSingleThreadExecutor();
    private final Handler           mMainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean mFetchStarted;
    private volatile boolean mDestroyed;
    private boolean mSerialRevealed;
    private SpringAnimation mLoaderFade;
    private String mImageTriedOem;
    private int mImageAttempts;
    // Set when a wiki request failed with an I/O error (offline, timeout) rather than a 404.
    private volatile boolean mImageNetworkError;

    public ModelPageController(Context context, String key) {
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
        applyTileShape(layoutPref);
        loadDeviceImage(layoutPref, codename,
                SystemProperties.get("ro.product.manufacturer", ""));

        // 2. Specs grid (synchronous — local props only)
        populateSpecsCard(layoutPref);
        bindOem(layoutPref, SystemProperties.get("ro.product.manufacturer", ""));

        // 3. Maintainer. Official builds resolve it via the OTA JSON first and
        //    only fall back to the overlay name if that yields no maintainer, so
        //    the name never flips. Unofficial builds use the overlay directly.
        if (isOfficialBuild()) {
            if (!mFetchStarted && !mExecutor.isShutdown() && !mDestroyed) {
                mFetchStarted = true;
                fetchOtaDataAndShow(layoutPref, codename);
            }
        } else {
            showOverlayMaintainerFallback(layoutPref);
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
        final String model = Build.MODEL;
        final String sku = SystemProperties.get("ro.boot.hardware.sku", "");
        final String cacheKey = "device_" + codename + "|" + model + "|" + sku;
        Bitmap cached = sBitmapCache.get(cacheKey);
        if (cached != null) {
            setImageLoading(layoutPref, false);
            imageView.setImageBitmap(cached);
            fadeIn(imageView, false);
            setVisible(layoutPref, R.id.device_image_placeholder, false);
            return;
        }

        // ── Step 3: network (LineageOS variant -> EVO -> Wikipedia) ──────────
        if (mExecutor.isShutdown()) return;
        if (knownOem.equalsIgnoreCase(mImageTriedOem)) return; // in flight / already failed
        if (mImageAttempts >= MAX_IMAGE_ATTEMPTS) return;
        mImageAttempts++;
        mImageTriedOem = knownOem;
        final String oem = knownOem;

        setImageLoading(layoutPref, true);
        mExecutor.execute(() -> {
            mImageNetworkError = false;
            Bitmap bmp = null;
            final String lineageUrl = resolveLineageImageUrl(codename, oem);
            if (lineageUrl != null) bmp = NetworkUtils.fetchBitmap(lineageUrl);
            if (bmp == null && !mDestroyed) {
                bmp = NetworkUtils.fetchBitmap(String.format(EVO_IMAGE_URL, codename));
            }
            if (bmp == null && !mDestroyed) bmp = fetchWikipediaImage(oem);
            final Bitmap result = bmp;
            mMainHandler.post(() -> {
                if (mDestroyed) return;
                setImageLoading(layoutPref, false);
                if (result != null) {
                    sBitmapCache.put(cacheKey, result);
                    imageView.setImageBitmap(result);
                    fadeIn(imageView, false);
                    setVisible(layoutPref, R.id.device_image_placeholder, false);
                } else if (mImageNetworkError) {
                    // Offline or timed out, not a real miss: keep the tile with the placeholder
                    // and let the next updateState() try again.
                    mImageTriedOem = null;
                } else {
                    // Nothing found anywhere: drop the tile, keep the card.
                    setVisible(layoutPref, R.id.device_image_tile, false);
                }
            });
        });
    }

    // ---- LineageOS wiki (variant aware) ------------------------------------

    /** Subset of a lineage_wiki _data/devices/*.yml entry. */
    private static final class WikiDevice {
        String codename = "", name = "", image = "", vendor = "", variant = "";
        final List<String> models = new ArrayList<>();
    }

    /**
     * Picks the wiki entry for this exact variant and returns its image URL.
     * Entries are scored: model/SKU in models[] (3) > marketname == name (2) >
     * codename only (1). Entries from another vendor are ignored.
     */
    private String resolveLineageImageUrl(String codename, String oem) {
        final String model = Build.MODEL;
        final String sku = SystemProperties.get("ro.boot.hardware.sku", "");
        final String market = SystemProperties.get("ro.product.marketname", "");

        final String cacheKey = "lineage_img_" + codename + "|" + model + "|" + sku;
        final String cachedImage = mPrefs.getString(cacheKey, "");
        if (!cachedImage.isEmpty()) return String.format(LINEAGE_IMAGE_URL, cachedImage);

        WikiDevice best = null;
        int bestScore = 0;
        for (String candidate : getCodenameCandidates(codename)) {
            for (WikiDevice d : fetchLineageDevices(candidate)) {
                if (!d.vendor.isEmpty() && !oem.isEmpty() && !d.vendor.equalsIgnoreCase(oem)) {
                    continue;
                }
                int score = 1;
                if (matchesAny(d.models, model) || matchesAny(d.models, sku)) {
                    score = 3;
                } else if (!market.isEmpty() && normalize(d.name).startsWith(normalize(market))) {
                    score = 2;
                }
                if (score > bestScore) {
                    best = d;
                    bestScore = score;
                }
            }
            if (best != null) break;
            if (mDestroyed) return null;
        }
        if (best == null) return null;

        final String image = best.image.isEmpty() ? best.codename + ".png" : best.image;
        mPrefs.edit().putString(cacheKey, image).apply();
        return String.format(LINEAGE_IMAGE_URL, image);
    }

    /** <codename>.yml, then <codename>_variantN.yml while they exist. */
    private List<WikiDevice> fetchLineageDevices(String codename) {
        final List<WikiDevice> out = new ArrayList<>();
        for (int i = 0; i <= LINEAGE_MAX_VARIANTS && !mDestroyed; i++) {
            final WikiDevice d = fetchLineageDevice(i == 0 ? codename : codename + "_variant" + i);
            if (d == null) {
                if (i == 0) continue; // base may not exist when only variants do
                break;
            }
            out.add(d);
            if (i == 0 && d.variant.isEmpty()) break; // single-variant device
        }
        return out;
    }

    private WikiDevice fetchLineageDevice(String fileName) {
        try {
            String yaml = NetworkUtils.fetchString(
                    String.format(LINEAGE_DEVICE_YML_URL, fileName), null);
            return yaml == null || yaml.isEmpty() ? null : parseWikiDevice(yaml);
        } catch (IOException e) {
            Log.d(TAG, "No lineage wiki entry " + fileName, e);
            mImageNetworkError = true;
            return null;
        }
    }

    private static WikiDevice parseWikiDevice(String yaml) {
        final WikiDevice d = new WikiDevice();
        final String[] lines = yaml.split("\n");
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            if (line.startsWith("codename:")) d.codename = yamlValue(line);
            else if (line.startsWith("name:")) d.name = yamlValue(line);
            else if (line.startsWith("image:")) d.image = yamlValue(line);
            else if (line.startsWith("vendor:")) d.vendor = yamlValue(line);
            else if (line.startsWith("variant:")) d.variant = yamlValue(line);
            else if (line.startsWith("models:")) {
                final String v = line.substring(line.indexOf(':') + 1).trim();
                if (v.startsWith("[")) {
                    int end = v.lastIndexOf(']');
                    if (end < 0) end = v.length();
                    for (String m : v.substring(1, end).split(",")) {
                        final String clean = unquote(m);
                        if (!clean.isEmpty()) d.models.add(clean);
                    }
                } else {
                    while (i + 1 < lines.length && lines[i + 1].trim().startsWith("- ")) {
                        d.models.add(unquote(lines[++i].trim().substring(2)));
                    }
                }
            }
        }
        return d.codename.isEmpty() ? null : d; // also rejects 404 bodies
    }

    private static String yamlValue(String line) {
        return unquote(line.substring(line.indexOf(':') + 1));
    }

    private static String unquote(String v) {
        return v.trim().replace("\"", "").replace("'", "");
    }

    private static boolean matchesAny(List<String> models, String value) {
        if (value == null || value.isEmpty()) return false;
        for (String m : models) if (m.equalsIgnoreCase(value)) return true;
        return false;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // ---- Wikipedia (last resort) -------------------------------------------

    private Bitmap fetchWikipediaImage(String oem) {
        final String market = SystemProperties.get("ro.product.marketname", "").trim();
        if (market.isEmpty()) return null;

        final Set<String> titles = new LinkedHashSet<>();
        titles.add(market);
        if (!oem.isEmpty() && !normalize(market).startsWith(normalize(oem))) {
            titles.add(oem + " " + market);
        }
        for (String title : titles) {
            if (mDestroyed) return null;
            Bitmap bmp = fetchWikipediaSummaryImage(title);
            if (bmp != null) return bmp;
        }

        // Exact titles missed: accept the top search hit only if it names the device.
        try {
            final String query = oem.isEmpty() || normalize(market).startsWith(normalize(oem))
                    ? market : oem + " " + market;
            final String json = NetworkUtils.fetchString(
                    String.format(WIKIPEDIA_SEARCH_URL, Uri.encode(query)), null);
            final JSONArray hits = new JSONObject(json).getJSONObject("query")
                    .getJSONArray("search");
            if (hits.length() == 0) return null;
            final String title = hits.getJSONObject(0).getString("title");
            return normalize(title).contains(normalize(market))
                    ? fetchWikipediaSummaryImage(title) : null;
        } catch (Exception e) {
            Log.d(TAG, "Wikipedia search failed for " + market, e);
            return null;
        }
    }

    private Bitmap fetchWikipediaSummaryImage(String title) {
        try {
            final String json = NetworkUtils.fetchString(String.format(WIKIPEDIA_SUMMARY_URL,
                    Uri.encode(title.replace(' ', '_'))), null);
            final JSONObject root = new JSONObject(json);
            if (!"standard".equals(root.optString("type"))) return null; // skip disambiguation
            final JSONObject thumb = root.optJSONObject("thumbnail");
            final String src = thumb == null ? "" : thumb.optString("source", "");
            return src.isEmpty() ? null : NetworkUtils.fetchBitmap(src);
        } catch (Exception e) {
            Log.d(TAG, "No Wikipedia image for " + title, e);
            return null;
        }
    }

    /**
     * Paints the device tile as a Cookie9Sided (the same shape as the maintainer avatar) in
     * the hero tint. It is only a background: the photo is fit inside the square tile and is
     * never clipped, so the whole device stays visible.
     */
    private void applyTileShape(LayoutPreference layoutPref) {
        final View tile = layoutPref.findViewById(R.id.device_image_tile);
        if (tile == null) return;
        tile.setBackground(ModelPageShapes.cookieBackground(
                mContext.getColor(R.color.model_hero_tint)));
    }

    /**
     * Shows or hides the Material 3 Expressive ContainedLoadingIndicator that covers the device
     * image slot. It brings its own cookie container, so the tile's static cookie is dropped
     * while it runs and restored when loading ends; the tint is the same, so the swap is seamless.
     *
     * <p>When loading ends the loader is not cut: it fades out on the slow effects spring while
     * the image fades in underneath, and its composition is only disposed once it has settled.
     */
    private void setImageLoading(LayoutPreference layoutPref, boolean loading) {
        ComposeView loader = layoutPref.findViewById(R.id.device_image_loading);
        if (loader == null) return;
        final View tile = layoutPref.findViewById(R.id.device_image_tile);
        if (tile != null) {
            tile.setBackground(loading ? null
                    : ModelPageShapes.cookieBackground(mContext.getColor(R.color.model_hero_tint)));
        }
        if (loading) setVisible(layoutPref, R.id.device_image_tile, true);
        setVisible(layoutPref, R.id.device_image_placeholder, !loading);

        // A new request always supersedes a fade that is still running.
        if (mLoaderFade != null) mLoaderFade.cancel();
        mLoaderFade = null;

        if (loading) {
            loader.setAlpha(1f);
            loader.setVisibility(View.VISIBLE);
            ModelPageShapes.setLoaderActive(loader, true);
        } else if (loader.getVisibility() == View.VISIBLE && !mDestroyed
                && ValueAnimator.areAnimatorsEnabled()) {
            final SpringAnimation fade = new SpringAnimation(loader, DynamicAnimation.ALPHA, 0f);
            fade.getSpring().setDampingRatio(SLOW_EFFECTS_DAMPING)
                    .setStiffness(SLOW_EFFECTS_STIFFNESS);
            fade.addEndListener((anim, canceled, value, velocity) -> {
                if (canceled) return;
                loader.setVisibility(View.GONE);
                ModelPageShapes.setLoaderActive(loader, false);
                loader.setAlpha(1f);
                mLoaderFade = null;
            });
            mLoaderFade = fade;
            fade.start();
        } else {
            loader.setVisibility(View.GONE);
            ModelPageShapes.setLoaderActive(loader, false);
        }
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
                HardwareInfoPreferenceController.getDeviceModel();
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
        final TextView valueView = row.findViewById(R.id.spec_value);
        valueView.setText(value);
        if (mContext.getResources().getConfiguration().fontScale >= LARGE_FONT_SCALE) {
            // Let long values wrap instead of being middle-ellipsized at large font sizes.
            valueView.setSingleLine(false);
            valueView.setMaxLines(2);
            valueView.setEllipsize(TextUtils.TruncateAt.END);
        }
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
        setActionLabels(content);
        content.setOnClickListener(v -> copyToClipboard(v, label, value, false));
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
        updateSerialDescription(valueView, serial);
        ViewCompat.replaceAccessibilityAction(content,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                mContext.getString(android.R.string.copy), null);

        content.setOnClickListener(v -> {
            mSerialRevealed = !mSerialRevealed;
            valueView.setText(mSerialRevealed ? serial : maskSerial(serial));
            updateSerialDescription(valueView, serial);
        });
        content.setOnLongClickListener(v -> {
            copyToClipboard(label, serial, true);
            return true;
        });
    }

    /** TalkBack would read the bullets literally; describe the masked value instead. */
    private void updateSerialDescription(TextView valueView, String serial) {
        if (mSerialRevealed) {
            valueView.setContentDescription(null);
            return;
        }
        final String tail = serial.length() <= 4 ? "" : serial.substring(serial.length() - 4);
        valueView.setContentDescription(tail.isEmpty()
                ? mContext.getString(R.string.model_serial_hidden)
                : mContext.getString(R.string.model_serial_hidden_ends_in, tail));
    }

    /** Announces the tap action as "Copy" instead of the generic "double tap to activate". */
    private void setActionLabels(View content) {
        ViewCompat.replaceAccessibilityAction(content,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                mContext.getString(android.R.string.copy), null);
    }

    private static String maskSerial(String serial) {
        final String dots = "\u2022\u2022\u2022\u2022";
        return serial.length() <= 4 ? dots : dots + serial.substring(serial.length() - 4);
    }

    /** Tap path: confirms the copy with a haptic as well. */
    private void copyToClipboard(View source, String label, String text, boolean sensitive) {
        copyToClipboard(label, text, sensitive);
        source.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
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
    // Segmented rows: stock outer radius (token), inner 4dp, shape morphs on press
    // -------------------------------------------------------------------------

    private void styleRows(LinearLayout list) {
        final int n = list.getChildCount();
        final float outer = dimenPx("settingslib_preference_corner_radius", 20);
        final float inner = mContext.getResources().getDimension(R.dimen.model_row_inner_radius);
        final float pressed =
                mContext.getResources().getDimension(R.dimen.model_row_pressed_radius);
        // M3 ListTokens.SegmentedGap is 2dp.
        final int gap = Math.round(dimenPx("settingslib_expressive_space_extrasmall1", 2));
        final int fill = colorByName("settingslib_materialColorSurfaceBright");
        final ColorStateList rippleColor =
                stateLayer(colorByName("settingslib_materialColorOnSurface"));

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
            // pressed / focused / hovered; the most emphatic state wins.
            final boolean[] state = new boolean[3];
            final float hoverRadius = mContext.getResources().getDimension(R.dimen.model_chip_radius);
            // Inner corners go 4dp -> 12dp on hover and 16dp on press; express hover as a
            // share of the pressed morph so a single spring covers every state.
            final float hoverEmphasis = pressed > inner
                    ? Math.max(0f, Math.min(1f, (hoverRadius - inner) / (pressed - inner))) : 0f;
            final CornerSpring corners =
                    new CornerSpring(row, bg, base, down, false, newFocusRing(row));
            final Runnable apply = () -> corners.to(
                    state[0] || state[1] ? 1f : state[2] ? hoverEmphasis : 0f);
            row.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        state[0] = true;
                        apply.run();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        state[0] = false;
                        apply.run();
                        break;
                    default:
                        break;
                }
                return false; // clicks and long-presses still fire
            });
            row.setOnFocusChangeListener((v, hasFocus) -> {
                state[1] = hasFocus;
                apply.run();
                corners.focus(hasFocus);
            });
            row.setOnHoverListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_HOVER_ENTER:
                        state[2] = true;
                        apply.run();
                        break;
                    case MotionEvent.ACTION_HOVER_EXIT:
                        state[2] = false;
                        apply.run();
                        break;
                    default:
                        break;
                }
                return false;
            });
        }
    }

    /**
     * Springs a view's corner radii (and optionally its scale) between a rest and a pressed shape.
     * One persistent spring is retargeted on every state change, so an interrupted press keeps
     * its velocity instead of restarting. Emphasis 0 is the rest shape, 1 the pressed shape.
     */
    private static final class CornerSpring {
        private final View mView;
        private final GradientDrawable mBg;
        private final float[] mRest;
        private final float[] mDown;
        private final float[] mCurrent = new float[8];
        private final SpringAnimation mCorners;
        private final FloatValueHolder mCornersHolder = new FloatValueHolder(0f);
        private final FloatValueHolder mRingHolder = new FloatValueHolder(0f);
        private final SpringAnimation mScaleX;
        private final SpringAnimation mScaleY;
        private final FocusRingDrawable mRing;
        private final SpringAnimation mRingSpring;

        CornerSpring(View view, GradientDrawable bg, float[] rest, float[] down, boolean scale,
                FocusRingDrawable ring) {
            mView = view;
            mBg = bg;
            mRest = rest;
            mDown = down;
            mRing = ring;
            System.arraycopy(rest, 0, mCurrent, 0, 8);
            if (ring != null) {
                ring.setRadii(mCurrent);
                mRingSpring = new SpringAnimation(mRingHolder);
                mRingSpring.setSpring(new SpringForce(0f)
                        .setDampingRatio(FAST_SPATIAL_DAMPING)
                        .setStiffness(FAST_SPATIAL_STIFFNESS));
                mRingSpring.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA);
                mRingSpring.addUpdateListener((anim, value, velocity) ->
                        mRing.setProgress(Math.max(0f, value)));
            } else {
                mRingSpring = null;
            }
            mCorners = new SpringAnimation(mCornersHolder);
            mCorners.setSpring(new SpringForce(0f)
                    .setDampingRatio(FAST_SPATIAL_DAMPING)
                    .setStiffness(FAST_SPATIAL_STIFFNESS));
            mCorners.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA);
            mCorners.addUpdateListener((anim, value, velocity) -> apply(value));
            if (scale) {
                mScaleX = scaleSpring(view, DynamicAnimation.SCALE_X);
                mScaleY = scaleSpring(view, DynamicAnimation.SCALE_Y);
            } else {
                mScaleX = null;
                mScaleY = null;
            }
        }

        private static SpringAnimation scaleSpring(View v, DynamicAnimation.ViewProperty prop) {
            final SpringAnimation a = new SpringAnimation(v, prop, 1f);
            a.getSpring().setDampingRatio(FAST_SPATIAL_DAMPING)
                    .setStiffness(FAST_SPATIAL_STIFFNESS);
            return a;
        }

        /**
         * Grows or shrinks the focus ring, like the Compose ripple: it springs in on the fast
         * spatial spec and out on the fast effects spec, so it never overshoots on the way out.
         */
        void focus(boolean focused) {
            if (mRingSpring == null) return;
            if (!ValueAnimator.areAnimatorsEnabled()) {
                snap(mRingSpring, mRingHolder, focused ? 1f : 0f);
                mRing.setProgress(focused ? 1f : 0f);
                return;
            }
            if (focused) {
                mRingSpring.getSpring().setDampingRatio(FAST_SPATIAL_DAMPING)
                        .setStiffness(FAST_SPATIAL_STIFFNESS);
            } else {
                mRingSpring.getSpring().setDampingRatio(FAST_EFFECTS_DAMPING)
                        .setStiffness(FAST_EFFECTS_STIFFNESS);
            }
            mRingSpring.animateToFinalPosition(focused ? 1f : 0f);
        }

        /**
         * SpringAnimation ignores the system animator scale, so with animations removed the
         * springs are cancelled and their end state is applied directly.
         */
        private static void snap(SpringAnimation anim, FloatValueHolder holder, float value) {
            if (anim.isRunning()) anim.cancel();
            holder.setValue(value);
        }

        void to(float emphasis) {
            if (!ValueAnimator.areAnimatorsEnabled()) {
                snap(mCorners, mCornersHolder, emphasis);
                apply(emphasis);
                if (mScaleX != null) {
                    final float scale = 1f - 0.05f * emphasis;
                    if (mScaleX.isRunning()) mScaleX.cancel();
                    if (mScaleY.isRunning()) mScaleY.cancel();
                    mView.setScaleX(scale);
                    mView.setScaleY(scale);
                }
                return;
            }
            mCorners.animateToFinalPosition(emphasis);
            if (mScaleX != null) {
                final float scale = 1f - 0.05f * emphasis;
                mScaleX.animateToFinalPosition(scale);
                mScaleY.animateToFinalPosition(scale);
            }
        }

        private void apply(float emphasis) {
            for (int k = 0; k < 8; k++) {
                mCurrent[k] = Math.max(0f, mRest[k] + (mDown[k] - mRest[k]) * emphasis);
            }
            mBg.setCornerRadii(mCurrent);
            mView.invalidateOutline();
            if (mRing != null) mRing.invalidateSelf();
        }
    }

    /**
     * Builds the focus ring for {@code view}, installs it as the view's foreground and returns it.
     * Sizes and colors follow the Material 3 inset focus ring: secondary outer stroke, onSecondary
     * inner stroke.
     */
    private FocusRingDrawable newFocusRing(View view) {
        final FocusRingDrawable ring = new FocusRingDrawable(
                dp(2), 0f, colorByName("settingslib_materialColorSecondary"),
                dp(3), dp(1), colorByName("settingslib_materialColorOnSecondary"));
        view.setForeground(ring);
        return ring;
    }

    /**
     * Fades in on the slow effects spring (no overshoot); surfaces also rise a few dp on the
     * default spatial spring as they settle.
     */
    private void fadeIn(View v, boolean rise) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            v.setAlpha(1f);
            v.setTranslationY(0f);
            return;
        }
        if (rise && v.getAlpha() < 1f) v.setTranslationY(
                mContext.getResources().getDimension(R.dimen.model_rise_offset));
        final SpringAnimation alpha = new SpringAnimation(v, DynamicAnimation.ALPHA, 1f);
        alpha.getSpring().setDampingRatio(SLOW_EFFECTS_DAMPING)
                .setStiffness(SLOW_EFFECTS_STIFFNESS);
        alpha.start();
        if (v.getTranslationY() != 0f) {
            final SpringAnimation riseSpring =
                    new SpringAnimation(v, DynamicAnimation.TRANSLATION_Y, 0f);
            riseSpring.getSpring().setDampingRatio(DEFAULT_SPATIAL_DAMPING)
                    .setStiffness(DEFAULT_SPATIAL_STIFFNESS);
            riseSpring.start();
        }
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

    /**
     * Material 3 state layer: {@code onColor} at 10% when pressed or focused and 8% when hovered,
     * nothing at rest. Hover and focus show as a tint, a press also starts the ripple.
     */
    private static ColorStateList stateLayer(int onColor) {
        return new ColorStateList(
                new int[][] {
                    {android.R.attr.state_pressed},
                    {android.R.attr.state_focused},
                    {android.R.attr.state_hovered},
                    {},
                },
                new int[] {
                    withAlpha(onColor, 0.10f),
                    withAlpha(onColor, 0.10f),
                    withAlpha(onColor, 0.08f),
                    0,
                });
    }

    private static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(255f * alpha) << 24);
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
     * Shows the maintainer card from the build_maintainer_summary overlay string.
     * Used directly on unofficial builds; official builds only reach the overlay
     * through fetchOtaDataAndShow() when the OTA JSON has no maintainer.
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
        View      support    = layoutPref.findViewById(R.id.maintainer_support_button);
        TextView  labelView  = layoutPref.findViewById(R.id.maintainer_label);
        ImageView avatarView = layoutPref.findViewById(R.id.maintainer_avatar);

        // Avatar and verified badge share the cookie clip.
        View avatarFrame = layoutPref.findViewById(R.id.maintainer_avatar_frame);
        if (avatarFrame != null) ModelPageShapes.clipToCookie(avatarFrame);
        if (badgeView != null) ModelPageShapes.clipToBadge(badgeView);

        if (labelView != null) labelView.setText(R.string.build_maintainer_label);
        if (nameView != null) nameView.setText(displayName);

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

            setupMaintainerActions(card, support, githubUrl, donateUrl);

            if (avatarView != null && data.hasGithub()) {
                loadGithubAvatar(layoutPref, data.github);
            }
        } else {
            if (badgeView != null) badgeView.setVisibility(View.GONE);
            final String donateUrl = UrlUtils.sanitizeUrl(overlayDonateUrl);
            setupMaintainerActions(card, support, null, donateUrl);
        }

        card.setVisibility(View.VISIBLE);
        fadeIn(card, true);
    }

    private void loadGithubAvatar(LayoutPreference layoutPref, String username) {
        ImageView av = layoutPref.findViewById(R.id.maintainer_avatar);
        if (av == null) return;
        GithubAvatarLoader.getInstance().loadIntoImageView(mContext, av, username);
    }

    /**
     * Wires up the maintainer actions.
     *
     * - Card row       → GitHub profile when known, otherwise not clickable
     * - Support button → donate link when present, otherwise hidden
     */
    private void setupMaintainerActions(View card, View support, String githubUrl,
            String donateUrl) {
        if (githubUrl != null) {
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> openUrl(githubUrl));
            styleMaintainerCard(card);
        } else {
            card.setClickable(false);
            card.setOnClickListener(null);
        }

        if (support == null) return;
        if (donateUrl == null) {
            support.setVisibility(View.GONE);
            return;
        }
        // A real M3 Button; its shape, state layer and focus indication come from Material.
        ModelPageShapes.setSupportButton((ComposeView) support, () -> openUrl(donateUrl));
        support.setVisibility(View.VISIBLE);
    }

    /** Rebuilds the card background in code so its corners can morph on press. */
    private void styleMaintainerCard(View card) {
        final float rest = mContext.getResources().getDimension(R.dimen.model_maintainer_radius);
        final float pressed =
                mContext.getResources().getDimension(R.dimen.model_row_pressed_radius);
        final float[] base = new float[8];
        final float[] down = new float[8];
        java.util.Arrays.fill(base, rest);
        java.util.Arrays.fill(down, pressed);

        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorByName("settingslib_materialColorSurfaceBright"));
        bg.setCornerRadii(base);
        card.setBackground(new RippleDrawable(
                stateLayer(colorByName("settingslib_materialColorOnSurface")), bg, null));
        card.setClipToOutline(true);

        final boolean[] state = new boolean[2]; // pressed, focused
        final CornerSpring corners =
                new CornerSpring(card, bg, base, down, false, newFocusRing(card));
        final Runnable apply = () -> corners.to(state[0] || state[1] ? 1f : 0f);
        card.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    state[0] = true;
                    apply.run();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    state[0] = false;
                    apply.run();
                    break;
                default:
                    break;
            }
            return false;
        });
        card.setOnFocusChangeListener((v, hasFocus) -> {
            state[1] = hasFocus;
            apply.run();
            corners.focus(hasFocus);
        });
    }

    // ---- OEM line -----------------------------------------------------------

    /**
     * Sets the hero's OEM line as "OEM | codename": local manufacturer first, replaced by the
     * OTA value. Plain text with no background.
     */
    private void bindOem(LayoutPreference layoutPref, String oemRaw) {
        TextView oemNameView = layoutPref.findViewById(R.id.oem_name);
        if (oemNameView == null) return;
        final String oem = oemRaw == null ? "" : oemRaw;
        final String codename = getCodename();
        final String line = oem.isEmpty() ? codename
                : codename.isEmpty() ? oem : oem + " | " + codename;
        oemNameView.setText(line);
        oemNameView.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
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

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void onDestroy() {
        mDestroyed = true;
        if (mLoaderFade != null) mLoaderFade.cancel();
        if (!mExecutor.isShutdown()) {
            mExecutor.shutdown();
        }
        // Do NOT shut down GithubAvatarLoader — it is a shared singleton.
    }
}
