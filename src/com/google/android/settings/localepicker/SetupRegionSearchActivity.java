package com.google.android.settings.localepicker;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.app.LocaleHelper;
import com.android.internal.app.LocaleStore;
import com.android.settings.overlay.FeatureFactory;

import com.google.android.setupcompat.partnerconfig.PartnerConfig;
import com.google.android.setupcompat.partnerconfig.PartnerConfigHelper;
import com.google.android.setupdesign.GlifRecyclerLayout;
import com.google.android.setupdesign.items.RecyclerItemAdapter;
import com.google.android.setupdesign.template.FloatingBackButtonMixin;
import com.google.android.setupdesign.template.HeaderMixin;
import com.google.android.setupdesign.util.ThemeHelper;
import com.android.settings.R;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SetupRegionSearchActivity extends Activity implements TextWatcher {
    private static final int REQUEST_LOCALE_PICKER = 0;

    private RecyclerItemAdapter mAdapter;
    private AppCompatImageView mBackIcon;
    private AppCompatImageView mClearSearchQueryButton;
    private boolean mIsNumberingMode;
    private LocaleStore.LocaleInfo mLocaleInfo;
    private List mLocaleOptions;
    private List mOriginalLocaleInfos;
    private CharSequence mPrefix;
    private RecyclerView mRecyclerView;
    private AppCompatEditText mSearchActionBarText;
    private SetupLocalePickerListController mSetupLocalePickerListController;
    private SearchFilter mSearchFilter = null;
    private ViewTreeObserver.OnGlobalFocusChangeListener mFocusChangeListener = null;

    @Override
    public void afterTextChanged(Editable s) {}

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    protected void onCreate(Bundle bundle) {
        applySuwTheme();
        super.onCreate(bundle);
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
        setContentView(R.layout.setup_search_region);
        setupContent();
    }

    private void setupContent() {
        View findManagedViewById;
        Bundle extras = getIntent().getExtras();
        mLocaleInfo = (LocaleStore.LocaleInfo) extras.getSerializable("extra_target_locale");
        mIsNumberingMode = extras.getBoolean("extra_is_numbering_system");
        boolean isSuwUseModalDialogEnabled =
                PartnerConfigHelper.isSuwUseModalDialogEnabled(getApplicationContext());
        GlifRecyclerLayout layout = findViewById(R.id.setup_wizard_region_search_layout);
        ((HeaderMixin) layout.getMixin(HeaderMixin.class)).getTextView().setVisibility(View.GONE);
        if (layout.getMixin(FloatingBackButtonMixin.class) != null) {
            ((FloatingBackButtonMixin) layout.getMixin(FloatingBackButtonMixin.class))
                    .setVisibility(View.GONE);
        }
        LinearLayout linearLayout =
                (LinearLayout)
                        layout.findManagedViewById(
                                com.google.android.setupdesign.R.id.sud_layout_header);
        if (linearLayout != null) {
            linearLayout.setVisibility(View.GONE);
        }
        ViewGroup viewGroup = findViewById(com.google.android.setupdesign.R.id.suc_layout_status);
        if (isSuwUseModalDialogEnabled) {
            viewGroup = findViewById(com.google.android.setupdesign.R.id.suc_intrinsic_size_layout);
        }
        if (viewGroup != null) {
            viewGroup.setClipChildren(true);
            View inflate = getLayoutInflater().inflate(R.layout.search_bar_layout, null);
            int childCount = viewGroup.getChildCount();
            if (viewGroup instanceof FrameLayout) {
                if (!ThemeHelper.shouldApplyGlifExpressiveStyle(getApplicationContext())
                        && (findManagedViewById =
                                        layout.findManagedViewById(
                                                com.google.android.setupdesign.R.id
                                                        .sud_landscape_content_area))
                                != null) {
                    findManagedViewById.setPadding(0, 0, 0, 0);
                }
                final View childAt = viewGroup.getChildAt(0);
                final FrameLayout.LayoutParams layoutParams =
                        (FrameLayout.LayoutParams) childAt.getLayoutParams();
                final View findViewById = inflate.findViewById(R.id.suw_locale_picker_search_bar);
                findViewById.post(
                        new Runnable() {
                            @Override
                            public final void run() {
                                layoutParams.topMargin =
                                        findViewById.getBottom() + (findViewById.getHeight() / 4);
                                childAt.setLayoutParams(layoutParams);
                            }
                        });
                if (isSuwUseModalDialogEnabled) {
                    inflate.setPadding(
                            inflate.getPaddingLeft(),
                            (int)
                                    PartnerConfigHelper.get(getApplicationContext())
                                            .getDimension(
                                                    getApplicationContext(),
                                                    PartnerConfig.CONFIG_ICON_MARGIN_TOP),
                            inflate.getPaddingRight(),
                            inflate.getPaddingBottom());
                    View findManagedViewById2 =
                            layout.findManagedViewById(
                                    com.google.android.setupdesign.R.id.sud_landscape_content_area);
                    if (findManagedViewById2 != null) {
                        findManagedViewById2.setPadding(
                                findManagedViewById2.getPaddingLeft(),
                                inflate.getHeight(),
                                findManagedViewById2.getPaddingRight(),
                                findManagedViewById2.getPaddingBottom());
                    }
                }
                viewGroup.addView(inflate, childCount);
            } else {
                int i = childCount + 1;
                final View[] viewArr = new View[i];
                viewArr[0] = inflate;
                int i2 = 0;
                while (i2 < childCount) {
                    int i3 = i2 + 1;
                    viewArr[i3] = viewGroup.getChildAt(i2);
                    i2 = i3;
                }
                viewGroup.removeAllViews();
                View findViewById2 =
                        viewArr[2].findViewById(
                                com.google.android.setupdesign.R.id.sud_landscape_content_area);
                if (findViewById2 != null) {
                    findViewById2.setPadding(
                            findViewById2.getPaddingStart(),
                            0,
                            findViewById2.getPaddingEnd(),
                            findViewById2.getPaddingBottom());
                }
                final LinearLayout.LayoutParams layoutParams2 =
                        (LinearLayout.LayoutParams) viewArr[2].getLayoutParams();
                final View findViewById3 = inflate.findViewById(R.id.suw_locale_picker_search_bar);
                findViewById3.post(
                        new Runnable() {
                            @Override
                            public final void run() {
                                layoutParams2.bottomMargin = (findViewById3.getHeight() * 3) / 2;
                                viewArr[2].setLayoutParams(layoutParams2);
                            }
                        });
                for (int i4 = 0; i4 < i; i4++) {
                    viewGroup.addView(viewArr[i4], i4);
                }
                ScrollView scrollView =
                        findViewById(com.google.android.setupdesign.R.id.sud_header_scroll_view);
                if (scrollView != null) {
                    scrollView.setContentDescription(
                            getApplicationContext().getString(R.string.search_region_page));
                }
            }
        }
        mBackIcon = findViewById(R.id.back_icon);
        if (mBackIcon != null) {
            mBackIcon.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public final void onClick(View view) {
                            FeatureFactory.getFeatureFactory()
                                    .getMetricsFeatureProvider()
                                    .action(
                                            getApplicationContext(),
                                            SettingsEnums
                                                    .ACTION_NO_PREFERRED_REGION_AFTER_SEARCH_REGION_IN_SUW,
                                            new Pair[0]);
                            finish();
                        }
                    });
        }
        mSearchActionBarText = findViewById(R.id.search_action_bar_text);
        if (mSearchActionBarText != null) {
            mSearchActionBarText.addTextChangedListener(this);
            mSearchActionBarText.requestFocus();
        }
        mClearSearchQueryButton = findViewById(R.id.clear_search_query);
        if (mClearSearchQueryButton != null) {
            mClearSearchQueryButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public final void onClick(View view) {
                            clearLanguageSearchText();
                        }
                    });
        }
        mRecyclerView = layout.getRecyclerView();
        mAdapter = (RecyclerItemAdapter) layout.getAdapter();
        LocaleSelectedListener localeSelectedListener =
                new LocaleSelectedListener() {
                    @Override
                    public void onLocaleSelected(LocaleStore.LocaleInfo localeInfo) {
                        Intent intent = new Intent();
                        intent.putExtra("localeInfo", (Serializable) localeInfo);
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
                            int i5 = findLastVisibleItemPosition + 1;
                            if (childAdapterPosition != findLastVisibleItemPosition
                                    || i5 >= itemCount) {
                                return;
                            }
                            mRecyclerView.smoothScrollToPosition(i5);
                        }
                    }
                };
        SetupLocalePickerListController setupLocalePickerListController =
                new SetupLocalePickerListController(
                        getApplicationContext(), this, mLocaleInfo, mIsNumberingMode);
        mSetupLocalePickerListController = setupLocalePickerListController;
        setupLocalePickerListController.setLocaleSelectedListener(localeSelectedListener);
        mSetupLocalePickerListController.displayScreen(mAdapter);
        if (mSetupLocalePickerListController != null) {
            mOriginalLocaleInfos = mSetupLocalePickerListController.getSupportedLocaleList();
            mOriginalLocaleInfos.addAll(mSetupLocalePickerListController.getSuggestedLocaleList());
        }
    }

    private void filterSearch(String str) {
        if (mSearchFilter == null) {
            mSearchFilter = new SearchFilter();
        }
        if (mOriginalLocaleInfos == null) {
            Log.w(
                    "SetupRegionSearchActivity",
                    "Locales haven't loaded completely yet, so nothing can be filtered");
        } else {
            mSearchFilter.filter(str);
        }
    }

    class SearchFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();
            mPrefix = prefix;
            if (TextUtils.isEmpty(prefix)) {
                results.values = mOriginalLocaleInfos;
                results.count = mOriginalLocaleInfos.size();
                return results;
            }
            List<LocaleStore.LocaleInfo> newList = new ArrayList<>(mOriginalLocaleInfos);
            Locale locale = Locale.getDefault();
            String prefixString = LocaleHelper.normalizeForSearch(prefix.toString(), locale);
            final ArrayList<LocaleStore.LocaleInfo> newValues = new ArrayList<>();
            final int count = newList.size();
            for (int i = 0; i < count; i++) {
                final LocaleStore.LocaleInfo value = newList.get(i);
                final String nameToCheck =
                        LocaleHelper.normalizeForSearch(value.getFullNameInUiLanguage(), locale);
                final String nativeNameToCheck =
                        LocaleHelper.normalizeForSearch(value.getFullNameNative(), locale);
                if (wordMatches(nativeNameToCheck, prefixString)
                        || wordMatches(nameToCheck, prefixString)) {
                    if (!newValues.contains(value)) {
                        newValues.add(value);
                    }
                }
            }
            results.values = newValues;
            results.count = newValues.size();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            if (mSetupLocalePickerListController == null) {
                Log.d("SetupRegionSearchActivity", "publishResults(), can not get item.");
                return;
            }
            mLocaleOptions = (ArrayList<LocaleStore.LocaleInfo>) results.values;
            mSetupLocalePickerListController.onSearchListChanged(mLocaleOptions, mPrefix);
        }

        private boolean wordMatches(String valueText, String prefixString) {
            if (valueText == null) {
                return false;
            }
            if (valueText.startsWith(prefixString)) {
                return true;
            }
            Matcher matcher = Pattern.compile("^.*?\\((.*)").matcher(valueText);
            if (matcher.find()) {
                return matcher.group(1).startsWith(prefixString);
            }
            return false;
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mClearSearchQueryButton != null) {
            mClearSearchQueryButton.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
        }
        filterSearch(s.toString());
    }

    private void clearLanguageSearchText() {
        Editable text;
        if (mSearchActionBarText != null && (text = mSearchActionBarText.getText()) != null) {
            text.clear();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LOCALE_PICKER && resultCode == RESULT_OK) {
            if (data != null) {
                LocaleStore.LocaleInfo serializableExtra =
                        (LocaleStore.LocaleInfo) data.getSerializableExtra("localeInfo");
                Intent intent = new Intent();
                intent.putExtra("localeInfo", (Serializable) serializableExtra);
                setResult(Activity.RESULT_OK, intent);
            }
            finish();
        }
    }
}
