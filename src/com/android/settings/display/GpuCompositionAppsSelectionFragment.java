package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.google.android.material.appbar.AppBarLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GpuCompositionAppsSelectionFragment extends SettingsPreferenceFragment {

    private static final int MENU_TURN_ON_ALL_APPS = Menu.FIRST;
    private static final int MENU_TURN_OFF_ALL_APPS = Menu.FIRST + 1;

    private static final Set<String> SYSTEM_PACKAGE_WHITELIST = new HashSet<>(Arrays.asList(
            "com.android.chrome",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music"
    ));

    private static final Set<String> SYSTEM_PACKAGE_BLACKLIST = new HashSet<>(Arrays.asList(
            "com.google.android.contactkeys",
            "com.google.android.safetycore",
            "com.google.ar.core"
    ));

    private PackageManager mPackageManager;
    private final Set<String> mEnabledApps = new HashSet<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mBackgroundExecutor = Executors.newSingleThreadExecutor();
    private List<ApplicationInfo> mAllApps = Collections.emptyList();
    private String mSearchQuery = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mPackageManager = requireActivity().getPackageManager();
        loadEnabledApps();

        if (savedInstanceState != null) {
            mSearchQuery = savedInstanceState.getString("search_query", "");
        }

        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getPrefContext()));
        loadAppsAsync();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBackgroundExecutor.shutdownNow();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("search_query", mSearchQuery);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.manage_apps, menu);

        menu.removeItem(R.id.advanced);
        menu.removeItem(R.id.show_system);
        menu.removeItem(R.id.hide_system);
        menu.removeItem(R.id.sort_order_alpha);
        menu.removeItem(R.id.sort_order_size);
        menu.removeItem(R.id.sort_order_recent_notification);
        menu.removeItem(R.id.sort_order_frequent_notification);
        menu.removeItem(R.id.reset_app_preferences);
        menu.removeItem(R.id.delete_all_app_clones);

        final MenuItem turnOnAllApps =
                menu.add(Menu.NONE, MENU_TURN_ON_ALL_APPS, Menu.NONE, "Turn on for all apps");
        turnOnAllApps.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        final MenuItem turnOffAllApps =
                menu.add(Menu.NONE, MENU_TURN_OFF_ALL_APPS, Menu.NONE, "Turn off for all apps");
        turnOffAllApps.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        final MenuItem searchMenu = menu.findItem(R.id.search_app_list_menu);
        if (searchMenu != null && searchMenu.getActionView() instanceof SearchView) {
            final RecyclerView listView = getListView();
            final AppBarLayout appBarLayout = requireActivity().findViewById(R.id.app_bar);
            searchMenu.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    if (appBarLayout != null) {
                        appBarLayout.setExpanded(false /* expanded */, false /* animate */);
                    }
                    ViewCompat.setNestedScrollingEnabled(listView, false);
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    if (appBarLayout != null) {
                        appBarLayout.setExpanded(false /* expanded */, false /* animate */);
                    }
                    ViewCompat.setNestedScrollingEnabled(listView, true);
                    return true;
                }
            });

            final SearchView searchView = (SearchView) searchMenu.getActionView();
            searchView.setQueryHint(getString(R.string.search_settings));
            searchView.setMaxWidth(Integer.MAX_VALUE);
            if (!TextUtils.isEmpty(mSearchQuery)) {
                searchMenu.expandActionView();
                searchView.setQuery(mSearchQuery, false);
                searchView.clearFocus();
            }
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    updateSearchQuery(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    updateSearchQuery(newText);
                    return true;
                }
            });
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == MENU_TURN_ON_ALL_APPS) {
            setAllAppsEnabled(true);
            return true;
        } else if (itemId == MENU_TURN_OFF_ALL_APPS) {
            setAllAppsEnabled(false);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateSearchQuery(@Nullable String query) {
        mSearchQuery = query == null ? "" : query;
        rebuildPreferenceList();
    }

    private void setAllAppsEnabled(boolean enabled) {
        mEnabledApps.clear();
        if (enabled) {
            for (ApplicationInfo app : mAllApps) {
                mEnabledApps.add(app.packageName);
            }
        }
        saveEnabledApps();
        rebuildPreferenceList();
    }

    private void loadAppsAsync() {
        mBackgroundExecutor.execute(() -> {
            final List<ApplicationInfo> apps = getUserInstalledApps();
            Collections.sort(apps, Comparator.comparing(
                    a -> a.loadLabel(mPackageManager).toString(),
                    String.CASE_INSENSITIVE_ORDER));

            if (!isAdded()) {
                return;
            }

            mMainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                mAllApps = apps;
                rebuildPreferenceList();
            });
        });
    }

    private void rebuildPreferenceList() {
        final PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }
        screen.removeAll();

        final String normalizedQuery = mSearchQuery.trim().toLowerCase(Locale.ROOT);
        for (ApplicationInfo app : mAllApps) {
            final CharSequence appLabel = app.loadLabel(mPackageManager);
            if (!normalizedQuery.isEmpty()
                    && !appLabel.toString().toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                continue;
            }
            screen.addPreference(createAppPreference(app, appLabel));
        }
    }

    private SwitchPreferenceCompat createAppPreference(ApplicationInfo app, CharSequence appLabel) {
        final SwitchPreferenceCompat pref = new SwitchPreferenceCompat(getPrefContext());
        pref.setKey(app.packageName);
        pref.setTitle(appLabel);
        pref.setIcon(getScaledAppIcon(app));
        pref.setPersistent(false);
        pref.setChecked(mEnabledApps.contains(app.packageName));
        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            final boolean enabled = Boolean.TRUE.equals(newValue);
            if (enabled) {
                mEnabledApps.add(app.packageName);
            } else {
                mEnabledApps.remove(app.packageName);
            }
            saveEnabledApps();
            return true;
        });
        return pref;
    }

    private Drawable getScaledAppIcon(ApplicationInfo app) {
        final Drawable icon = app.loadIcon(mPackageManager);
        final int iconSizePx = getResources().getDimensionPixelSize(R.dimen.app_icon_size);
        final Bitmap bitmap = Bitmap.createBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        icon.setBounds(0, 0, iconSizePx, iconSizePx);
        icon.draw(canvas);
        return new BitmapDrawable(getResources(), bitmap);
    }

    private void loadEnabledApps() {
        final String enabledAppsString = Settings.Secure.getString(
                requireActivity().getContentResolver(),
                Settings.Secure.DISABLE_HW_OVERLAYS_APPS);

        mEnabledApps.clear();
        if (!TextUtils.isEmpty(enabledAppsString)) {
            mEnabledApps.addAll(Arrays.asList(enabledAppsString.split(",")));
        }
    }

    private List<ApplicationInfo> getUserInstalledApps() {
        final List<ApplicationInfo> allApps = mPackageManager.getInstalledApplications(0);
        final List<ApplicationInfo> filteredApps = new ArrayList<>();

        for (ApplicationInfo app : allApps) {
            final boolean isSystem = (app.flags
                    & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            final boolean inWhitelist = SYSTEM_PACKAGE_WHITELIST.contains(app.packageName);
            final boolean inBlacklist = SYSTEM_PACKAGE_BLACKLIST.contains(app.packageName);
            if ((!isSystem || inWhitelist) && !inBlacklist) {
                filteredApps.add(app);
            }
        }
        return filteredApps;
    }

    private void saveEnabledApps() {
        final String enabledAppsString = TextUtils.join(",", mEnabledApps);
        Settings.Secure.putString(
                requireActivity().getContentResolver(),
                Settings.Secure.DISABLE_HW_OVERLAYS_APPS,
                enabledAppsString);
    }
}
