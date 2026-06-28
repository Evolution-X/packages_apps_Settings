package com.google.android.settings.localepicker;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.android.internal.app.LocaleHelper;
import com.android.internal.app.LocaleStore;

import com.google.android.setupdesign.items.Item;
import com.android.settings.R;

import java.util.Locale;

public class LocaleItem extends Item {
    private boolean mIsCountryMode;
    private boolean mIsSuggested;
    private LocaleStore.LocaleInfo mLocaleInfo;

    public LocaleItem() {
        mIsSuggested = false;
        mIsCountryMode = false;
        setLayoutResource(R.layout.locale_item);
    }

    public LocaleItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIsSuggested = false;
        mIsCountryMode = false;
        setLayoutResource(R.layout.locale_item);
    }

    public void setLocaleInfo(LocaleStore.LocaleInfo localeInfo) {
        mLocaleInfo = localeInfo;
    }

    public LocaleStore.LocaleInfo getLocaleInfo() {
        return mLocaleInfo;
    }

    public void setSuggestedState(boolean isSuggested) {
        mIsSuggested = isSuggested;
    }

    public boolean isSuggested() {
        return mIsSuggested;
    }

    public void setCountryMode(boolean isCountryMode) {
        mIsCountryMode = isCountryMode;
    }

    @Override
    public void onBindView(View view) {
        super.onBindView(view);
        if (mLocaleInfo != null) {
            applyTalkBackPronunciationFix(view);
        }
    }

    private void applyTalkBackPronunciationFix(View view) {
        view.setContentDescription(getTranslatedLocaleName());
    }

    private String getTranslatedLocaleName() {
        Locale locale = mLocaleInfo.getLocale();
        if (mIsCountryMode) {
            return LocaleHelper.getDisplayCountry(locale);
        }
        return LocaleHelper.getDisplayName(locale.stripExtensions(), true);
    }
}
