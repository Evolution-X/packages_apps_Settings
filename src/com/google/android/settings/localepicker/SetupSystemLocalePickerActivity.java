package com.google.android.settings.localepicker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ScrollView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.app.LocaleStore;

import com.google.android.setupdesign.GlifRecyclerLayout;
import com.google.android.setupdesign.items.RecyclerItemAdapter;
import com.google.android.setupdesign.template.HeaderMixin;
import com.google.android.setupdesign.util.ThemeHelper;
import com.android.settings.R;

import java.io.Serializable;

public class SetupSystemLocalePickerActivity extends Activity {
    private RecyclerItemAdapter mAdapter;
    private ViewTreeObserver.OnGlobalFocusChangeListener mFocusChangeListener = null;
    private RecyclerView mRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySuwTheme();
        super.onCreate(savedInstanceState);
        setupLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mRecyclerView != null) {
            mRecyclerView
                    .getViewTreeObserver()
                    .addOnGlobalFocusChangeListener(mFocusChangeListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mRecyclerView != null) {
            mRecyclerView
                    .getViewTreeObserver()
                    .removeOnGlobalFocusChangeListener(mFocusChangeListener);
        }
    }

    private void applySuwTheme() {
        setTheme(R.style.Theme_Settings_LocalePicker);
        if (ThemeHelper.shouldApplyGlifExpressiveStyle(getApplicationContext())) {
            if (ThemeHelper.trySetSuwTheme(this)) {
                return;
            }
            setTheme(ThemeHelper.getSuwDefaultTheme(getApplicationContext()));
            ThemeHelper.trySetDynamicColor(this);
            return;
        }
        ThemeHelper.applyTheme(this);
        ThemeHelper.trySetDynamicColor(this);
    }

    private void setupLayout() {
        setContentView(R.layout.setup_language_picker);
        setupContent();
    }

    private void setupContent() {
        GlifRecyclerLayout layout = findViewById(R.id.setup_wizard_language_picker_layout);
        ((HeaderMixin) layout.getMixin(HeaderMixin.class)).getTextView().setVisibility(View.GONE);
        ImageView imageView = findViewById(com.google.android.setupdesign.R.id.sud_layout_icon);
        if (getResources().getBoolean(R.bool.config_force_showing_icon_on_locale_picker_screen)
                && imageView != null) {
            TypedValue typedValue = new TypedValue();
            if (getTheme()
                    .resolveAttribute(
                            com.google.android.setupdesign.R.attr.sudGlifIconSize,
                            typedValue,
                            true)) {
                int dimension = (int) typedValue.getDimension(getResources().getDisplayMetrics());
                ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.width = dimension;
                    layoutParams.height = dimension;
                    imageView.setLayoutParams(layoutParams);
                }
            }
        }
        mRecyclerView = layout.getRecyclerView();
        mAdapter = (RecyclerItemAdapter) layout.getAdapter();
        LocaleSelectedListener localeSelectedListener =
                new LocaleSelectedListener() {
                    @Override
                    public void onLocaleSelected(LocaleStore.LocaleInfo localeInfo) {
                        int isSuggestedLocale =
                                Settings.Global.getInt(
                                        getApplicationContext().getContentResolver(),
                                        "is_suggested_locale",
                                        0);
                        Intent intent = new Intent();
                        intent.putExtra("localeInfo", (Serializable) localeInfo);
                        intent.putExtra("EXTRA_IS_SUGGESTED_LOCALE", isSuggestedLocale);
                        setResult(Activity.RESULT_OK, intent);
                        finish();
                    }
                };
        mFocusChangeListener =
                new ViewTreeObserver.OnGlobalFocusChangeListener() {
                    @Override
                    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                        if (mRecyclerView == null
                                || mAdapter == null
                                || newFocus == null
                                || newFocus.getParent() != mRecyclerView) {
                            return;
                        }
                        RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
                        if (layoutManager instanceof LinearLayoutManager) {
                            LinearLayoutManager linearLayoutManager =
                                    (LinearLayoutManager) layoutManager;
                            int childAdapterPosition =
                                    mRecyclerView.getChildAdapterPosition(newFocus);
                            int itemCount = mAdapter.getItemCount();
                            if (childAdapterPosition == -1 || childAdapterPosition >= itemCount) {
                                return;
                            }
                            int findLastVisibleItemPosition =
                                    linearLayoutManager.findLastVisibleItemPosition();
                            int i = findLastVisibleItemPosition + 1;
                            if (childAdapterPosition != findLastVisibleItemPosition
                                    || i >= itemCount) {
                                return;
                            }
                            mRecyclerView.smoothScrollToPosition(i);
                        }
                    }
                };
        ScrollView scrollView =
                findViewById(com.google.android.setupdesign.R.id.sud_header_scroll_view);
        if (scrollView != null) {
            scrollView.setContentDescription(
                    getApplicationContext().getString(R.string.language_picker_page));
        }
        SetupLocalePickerListController controller =
                new SetupLocalePickerListController(getApplicationContext(), this, null, false);
        controller.setLocaleSelectedListener(localeSelectedListener);
        String psku = getIntent().getExtras().getString("extra_psku");
        if (psku == null) {
            Log.d("SetupSystemLocalePickerActivity", "No suggested PSKU");
            psku = "";
        }
        controller.setPsku(psku);
        controller.displayScreen(mAdapter);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                int isSuggestedLocale =
                        Settings.Global.getInt(
                                getApplicationContext().getContentResolver(),
                                "is_suggested_locale",
                                0);
                LocaleStore.LocaleInfo serializableExtra =
                        (LocaleStore.LocaleInfo) data.getSerializableExtra("localeInfo");
                Intent intent = new Intent();
                intent.putExtra(
                        "EXTRA_SELECTED_LOCALE", serializableExtra.getLocale().toLanguageTag());
                intent.putExtra("EXTRA_IS_SUGGESTED_LOCALE", isSuggestedLocale);
                setResult(Activity.RESULT_OK, intent);
            }
            finish();
        }
    }
}
