package com.google.android.settings.localepicker;

import android.content.Context;
import android.content.res.TypedArray;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.setupdesign.R;
import com.google.android.setupdesign.items.AbstractItem;
import com.google.android.setupdesign.util.ItemStyler;
import com.google.android.setupdesign.util.LayoutStyler;
import com.google.android.setupdesign.util.ThemeHelper;

public class SearchBarItem extends AbstractItem {
    private boolean mEnabled;
    private int mLayoutRes;
    private boolean mShouldApplyGlifExpressiveStyle;
    private boolean mVisible;

    @Override
    public boolean isGroupDivider() {
        return true;
    }

    public SearchBarItem() {
        mEnabled = true;
        mVisible = true;
        mLayoutRes = getDefaultLayoutResource();
    }

    public SearchBarItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        mEnabled = true;
        mVisible = true;
        mShouldApplyGlifExpressiveStyle = ThemeHelper.shouldApplyGlifExpressiveStyle(context);
        TypedArray styledAttrs = context.obtainStyledAttributes(attrs, R.styleable.SudItem);
        mEnabled = styledAttrs.getBoolean(R.styleable.SudItem_android_enabled, true);
        mLayoutRes =
                styledAttrs.getResourceId(
                        R.styleable.SudItem_android_layout, getDefaultLayoutResource());
        mVisible = styledAttrs.getBoolean(R.styleable.SudItem_android_visible, true);
        styledAttrs.recycle();
    }

    protected int getDefaultLayoutResource() {
        if (mShouldApplyGlifExpressiveStyle) {
            return com.android.settings.R.layout.search_bar_expressive_item;
        }
        return com.android.settings.R.layout.search_bar_item;
    }

    @Override
    public int getCount() {
        return isVisible() ? 1 : 0;
    }

    @Override
    public boolean isEnabled() {
        return mEnabled;
    }

    @Override
    public int getLayoutResource() {
        return mLayoutRes;
    }

    public void setVisible(boolean visible) {
        if (mVisible == visible) {
            return;
        }
        mVisible = visible;
        if (!visible) {
            notifyItemRangeRemoved(0, 1);
        } else {
            notifyItemRangeInserted(0, 1);
        }
    }

    public boolean isVisible() {
        return mVisible;
    }

    @Override
    public void onBindView(View view) {
        if (!mShouldApplyGlifExpressiveStyle) {
            LayoutStyler.applyPartnerCustomizationLayoutPaddingStyle(view);
        }
        Context context = view.getContext();
        if (Settings.Secure.getInt(context.getContentResolver(), "user_setup_complete", 0) != 1) {
            ItemStyler.applyFocusRingDrawable(
                    context, view, ItemStyler.FocusIndicatorShape.RECTANGLE, null);
        }
    }
}
