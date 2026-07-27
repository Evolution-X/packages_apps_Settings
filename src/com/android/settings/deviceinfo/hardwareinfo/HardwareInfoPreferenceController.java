/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.hardwareinfo;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
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
            "https://raw.githubusercontent.com/Evolution-X/OTA/bka/builds/%s.json";

    // -------------------------------------------------------------------------
    // Disk-cache SharedPreferences name
    // -------------------------------------------------------------------------

    private static final String PREFS_NAME            = "evolution_maintainer_cache";
    private static final int    CACHE_VERSION          = 2;
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

    private final SharedPreferences mPrefs;
    private final ExecutorService   mExecutor    = Executors.newSingleThreadExecutor();
    private final Handler           mMainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean mFetchStarted;
    private volatile boolean mDestroyed;

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
        showInfoCardLocalFallback(layoutPref);

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
                String overlayCacheKey = "overlay_" + resId;
                Bitmap overlayCached = sBitmapCache.get(overlayCacheKey);
                if (overlayCached != null) {
                    imageView.setImageBitmap(overlayCached);
                    imageView.animate().alpha(1f).setDuration(300).start();
                    return;
                }
                if (mExecutor.isShutdown()) return;
                mExecutor.execute(() -> {
                    Bitmap raw = android.graphics.BitmapFactory.decodeResource(
                            mContext.getResources(), resId);
                    final Bitmap result = raw != null ? softEdges(raw) : null;
                    mMainHandler.post(() -> {
                        if (result != null) {
                            sBitmapCache.put(overlayCacheKey, result);
                            imageView.setImageBitmap(result);
                        } else {
                            imageView.setImageResource(resId);
                        }
                        imageView.animate().alpha(1f).setDuration(300).start();
                    });
                });
                return;
            }
            Log.w(TAG, "config_deviceImageDrawable set to '" + overlayDrawableName
                    + "' but drawable not found — falling back to network");
        }

        // ── Step 2: memory cache ─────────────────────────────────────────────
        String cacheKey = "device_" + codename;
        Bitmap cached = sBitmapCache.get(cacheKey);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            imageView.animate().alpha(1f).setDuration(300).start();
            return;
        }

        // ── Step 3: network (EVO → LineageOS) ────────────────────────────────
        if (mExecutor.isShutdown()) return;
        final String evoUrl     = String.format(EVO_IMAGE_URL, codename);
        final String lineageUrl = String.format(LINEAGE_IMAGE_URL, codename);
        final String oem        = knownOem;

        mExecutor.execute(() -> {
            Bitmap bmp = NetworkUtils.fetchBitmap(evoUrl);
            if (bmp == null && !oem.isEmpty() && lineageOemMatches(codename, oem)) {
                bmp = NetworkUtils.fetchBitmap(lineageUrl);
            }
            final Bitmap result = bmp != null ? softEdges(bmp) : null;
            mMainHandler.post(() -> {
                if (result != null) {
                    sBitmapCache.put(cacheKey, result);
                    imageView.setImageBitmap(result);
                    imageView.animate().alpha(1f).setDuration(300).start();
                }
            });
        });
    }

    /**
     * Applies a soft-edge fade to the left and right sides of the device image only.
     * No fade at the top; a very subtle fade at the bottom to avoid hard cutoff.
     */
    private Bitmap softEdges(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();

        android.graphics.Bitmap output =
                android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(output);

        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setDither(true);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(src, 0, 0, paint);

        android.graphics.Paint fadePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        fadePaint.setXfermode(new android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.DST_IN));

        android.graphics.Bitmap mask =
                android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas maskCanvas = new android.graphics.Canvas(mask);
        maskCanvas.drawColor(0xFFFFFFFF);

        android.graphics.Paint edgePaint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        int fadeWidth  = Math.max(1, (int) (w * 0.08f));
        int fadeBottom = Math.max(1, (int) (h * 0.04f));

        android.graphics.LinearGradient leftGrad = new android.graphics.LinearGradient(
                0, 0, fadeWidth, 0,
                new int[]{0x00FFFFFF, 0xFFFFFFFF}, null,
                android.graphics.Shader.TileMode.CLAMP);
        edgePaint.setXfermode(new android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.DST_IN));
        edgePaint.setShader(leftGrad);
        maskCanvas.drawRect(0, 0, fadeWidth, h, edgePaint);

        android.graphics.LinearGradient rightGrad = new android.graphics.LinearGradient(
                w - fadeWidth, 0, w, 0,
                new int[]{0xFFFFFFFF, 0x00FFFFFF}, null,
                android.graphics.Shader.TileMode.CLAMP);
        edgePaint.setShader(rightGrad);
        maskCanvas.drawRect(w - fadeWidth, 0, w, h, edgePaint);

        android.graphics.LinearGradient bottomGrad = new android.graphics.LinearGradient(
                0, h - fadeBottom, 0, h,
                new int[]{0xFFFFFFFF, 0xAAFFFFFF}, null,
                android.graphics.Shader.TileMode.CLAMP);
        edgePaint.setShader(bottomGrad);
        maskCanvas.drawRect(0, h - fadeBottom, w, h, edgePaint);

        canvas.drawBitmap(mask, 0, 0, fadePaint);
        mask.recycle();
        return output;
    }

    // -------------------------------------------------------------------------
    // Section 2 — Specs grid (synchronous)
    // -------------------------------------------------------------------------

    private void populateSpecsCard(LayoutPreference layoutPref) {
        setText(layoutPref, R.id.spec_model_value,       getModel());
        setText(layoutPref, R.id.spec_serial_value,      getSerial());
        setText(layoutPref, R.id.spec_soc_value,         getSoc());
        setText(layoutPref, R.id.spec_ram_value,         getTotalRam());
        setText(layoutPref, R.id.spec_hw_revision_value, getHardwareRevision());

        String sku = SystemProperties.get("ro.boot.product.hardware.sku", "");
        View skuContainer = layoutPref.findViewById(R.id.spec_sku_container);
        if (!sku.isEmpty() && skuContainer != null) {
            setText(layoutPref, R.id.spec_sku_value, sku);
            skuContainer.setVisibility(View.VISIBLE);
        }
    }

    private String getModel() {
        String market = SystemProperties.get("ro.product.marketname", "");
        return market.isEmpty() ? Build.MODEL : market;
    }

    @SuppressWarnings("HardwareIds")
    private String getSerial() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Build.getSerial();
            }
        } catch (SecurityException e) {
            Log.w(TAG, "No READ_PHONE_STATE for serial", e);
        }
        String s = SystemProperties.get("ro.serialno", "");
        return s.isEmpty() ? mContext.getString(R.string.device_info_not_available) : s;
    }

    private String getSoc() {
        String s = SystemProperties.get("ro.soc.model", "");
        if (!s.isEmpty()) return s;
        s = SystemProperties.get("ro.board.platform", "");
        if (!s.isEmpty()) return s;
        return mContext.getString(R.string.device_info_not_available);
    }

    private String getTotalRam() {
        ActivityManager am =
                (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return mContext.getString(R.string.device_info_not_available);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long rounded = roundRamToTier(mi.totalMem / (1024 * 1024));
        if (rounded >= 1024) {
            float gb = rounded / 1024f;
            return (gb == (long) gb)
                    ? (long) gb + " GB"
                    : String.format(java.util.Locale.getDefault(), "%.1f GB", gb);
        }
        return rounded + " MB";
    }

    private long roundRamToTier(long mb) {
        long[] tiers = {1024, 2048, 4096, 6144, 8192, 12288, 16384, 24576};
        long best = mb, bestDelta = Long.MAX_VALUE;
        for (long t : tiers) {
            long d = Math.abs(mb - t);
            if (d < bestDelta) { bestDelta = d; best = t; }
        }
        return best;
    }

    private String getHardwareRevision() {
        for (String prop : new String[]{
                "ro.boot.hardware.revision", "ro.hardware.revision", "ro.boot.hwrev"}) {
            String v = SystemProperties.get(prop, "");
            if (!v.isEmpty()) return v;
        }
        return mContext.getString(R.string.device_info_not_available);
    }

    // -------------------------------------------------------------------------
    // Section 3 — Single async OTA fetch: maintainer card + OEM card
    // -------------------------------------------------------------------------

    /**
     * Shows the OEM/specs card immediately using only local system properties.
     * Will be overwritten by showInfoCard() if OTA fetch succeeds and provides
     * a better OEM name.
     */
    private void showInfoCardLocalFallback(LayoutPreference layoutPref) {
        String oem = SystemProperties.get("ro.product.manufacturer", "");
        if (!oem.isEmpty()) {
            showInfoCard(layoutPref, oem);
        }
    }

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

            for (String candidate : getCodenameCandidates(codename)) {
                data = resolveOtaData(candidate);
                if (data != null) break;
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
                showInfoCard(layoutPref, finalOem);
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

        if (nameView != null) nameView.setText(displayName);

        if (badgeView != null) {
            badgeView.setImageTintList(null);
        }
        if (chevron != null) {
            int[] attrsS = new int[]{android.R.attr.colorControlNormal};
            android.content.res.TypedArray taS =
                    chevron.getContext().obtainStyledAttributes(attrsS);
            int colorOnSurface = taS.getColor(0, 0xFF000000);
            taS.recycle();
            chevron.setImageTintList(
                    android.content.res.ColorStateList.valueOf(colorOnSurface));
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
        card.animate().alpha(1f).setDuration(300).start();
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

    // ---- OEM + specs card ---------------------------------------------------

    private void showInfoCard(LayoutPreference layoutPref, String oemRaw) {
        View infoCard = layoutPref.findViewById(R.id.info_card);
        if (infoCard == null) return;

        TextView oemNameView = layoutPref.findViewById(R.id.oem_name);
        if (oemNameView != null) {
            if (oemRaw != null && !oemRaw.isEmpty()) {
                oemNameView.setText(oemRaw);
                oemNameView.setVisibility(View.VISIBLE);
            } else {
                oemNameView.setVisibility(View.GONE);
            }
        }

        infoCard.setVisibility(View.VISIBLE);
        infoCard.animate().alpha(1f).setDuration(300).start();
    }

    // -------------------------------------------------------------------------
    // OTA JSON resolution  (disk cache + ETag revalidation via HttpCachePrefs)
    // -------------------------------------------------------------------------

    private OtaEntry resolveOtaData(String codename) {
        HttpCachePrefs cache = new HttpCachePrefs(mPrefs, codename);
        boolean hasCached = mPrefs.getBoolean(KEY_HAS_ENTRY_PREFIX + codename, false);

        if (hasCached && !cache.isStale()) {
            return readCachedOtaData(codename);
        }
        return fetchOtaDataForCodename(codename, cache);
    }

    private OtaEntry readCachedOtaData(String codename) {
        if (!mPrefs.getBoolean(KEY_HAS_ENTRY_PREFIX + codename, false)) return null;
        String maintainer = UrlUtils.trimToEmpty(mPrefs.getString(KEY_MAINTAINER_PREFIX + codename, ""));
        return new OtaEntry(
                maintainer.isEmpty() ? null : maintainer,
                mPrefs.getString(KEY_GITHUB_PREFIX      + codename, ""),
                mPrefs.getString(KEY_DONATE_PREFIX      + codename, ""),
                mPrefs.getString("oem_"                 + codename, ""),
                mPrefs.getBoolean(KEY_MAINTAINED_PREFIX + codename, false)
        );
    }

    private OtaEntry fetchOtaDataForCodename(String codename, HttpCachePrefs cache) {
        try {
            NetworkUtils.FetchResult r = NetworkUtils.fetchWithStatus(
                    String.format(EVO_OTA_URL, codename),
                    cache.buildHeaders(null));

            if (r.isNotModified()) {
                cache.touchLastCheck();
                return readCachedOtaData(codename);
            }

            if (!r.isOk() || r.bytes == null) {
                return readCachedOtaData(codename);
            }

            JSONObject root     = new JSONObject(r.bodyAsString());
            JSONArray  response = root.optJSONArray("response");
            if (response == null || response.length() == 0) {
                clearCache(codename, cache);
                return null;
            }

            JSONObject entry = response.optJSONObject(0);
            if (entry == null) { clearCache(codename, cache); return null; }

            boolean maintained = entry.optBoolean("currently_maintained", false);
            String  maintainer = UrlUtils.trimToEmpty(entry.optString("maintainer", null));
            String  paypal     = UrlUtils.trimToEmpty(entry.optString("paypal",     null));
            String  github     = UrlUtils.trimToEmpty(entry.optString("github",     null));
            String  oem        = UrlUtils.trimToEmpty(entry.optString("oem",        null));

            if (!maintained || maintainer.isEmpty()) {
                clearCache(codename, cache);
                if (!oem.isEmpty()) {
                    return new OtaEntry(null, null, null, oem, false);
                }
                return null;
            }

            mPrefs.edit()
                    .putBoolean(KEY_HAS_ENTRY_PREFIX  + codename, true)
                    .putBoolean(KEY_MAINTAINED_PREFIX + codename, maintained)
                    .putString(KEY_MAINTAINER_PREFIX  + codename, maintainer)
                    .putString(KEY_DONATE_PREFIX      + codename, paypal)
                    .putString(KEY_GITHUB_PREFIX      + codename, github)
                    .putString("oem_"                 + codename, oem)
                    .apply();
            cache.write(r.etag, r.lastModified);

            return new OtaEntry(maintainer, github, paypal, oem, maintained);

        } catch (Exception e) {
            Log.d(TAG, "OTA fetch failed for " + codename, e);
            return readCachedOtaData(codename);
        }
    }

    private void clearCache(String codename, HttpCachePrefs cache) {
        mPrefs.edit()
                .putBoolean(KEY_HAS_ENTRY_PREFIX  + codename, false)
                .remove(KEY_MAINTAINER_PREFIX     + codename)
                .remove(KEY_MAINTAINED_PREFIX     + codename)
                .remove(KEY_DONATE_PREFIX         + codename)
                .remove(KEY_GITHUB_PREFIX         + codename)
                .remove("oem_"                    + codename)
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
