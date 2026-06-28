package com.google.android.settings.localepicker;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.setupdesign.R;
import com.google.android.setupdesign.items.AbstractItem;
import com.google.android.setupdesign.util.LayoutStyler;
import com.google.android.setupdesign.util.ThemeHelper;

public class DividerItem extends AbstractItem {
    private boolean mEnabled;
    private int mLayoutRes;
    private boolean mShouldApplyGlifExpressiveStyle;
    private boolean mVisible;

    @Override
    public boolean isGroupDivider() {
        return true;
    }

    public DividerItem() {
        mEnabled = true;
        mVisible = true;
        mLayoutRes = getDefaultLayoutResource();
    }

    public DividerItem(Context context, AttributeSet attrs) {
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
        return com.android.settings.R.layout.divider_item;
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

    public void setVisible(boolean z) {
        if (mVisible == z) {
            return;
        }
        mVisible = z;
        if (!z) {
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
        if (mShouldApplyGlifExpressiveStyle) {
            return;
        }
        LayoutStyler.applyPartnerCustomizationLayoutPaddingStyle(view);
    }
}
