package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

public class ExpandDividerPreference extends PreferenceGroup {
    ImageView mImageView;
    private boolean mIsExpanded;
    private OnExpandListener mOnExpandListener;
    TextView mTextView;

    public interface OnExpandListener {
        void onExpand(boolean z);
    }

    public ExpandDividerPreference(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mIsExpanded = false;
        setLayoutResource(R.layout.preference_expand_divider);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder preferenceViewHolder) {
        super.onBindViewHolder(preferenceViewHolder);
        this.mTextView = (TextView) preferenceViewHolder.findViewById(R.id.expand_title);
        this.mImageView = (ImageView) preferenceViewHolder.findViewById(R.id.expand_icon);
        refreshUi();
    }

    @Override
    public void onClick() {
        setExpanded(!this.mIsExpanded);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(null, super.onSaveInstanceState());
        bundle.putBoolean("expand_state", this.mIsExpanded);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable parcelable) {
        Bundle bundle = (Bundle) parcelable;
        super.onRestoreInstanceState(bundle.getParcelable(null));
        setExpanded(bundle.getBoolean("expand_state"));
    }

    public void setExpanded(boolean z) {
        this.mIsExpanded = z;
        refreshUi();
        OnExpandListener onExpandListener = this.mOnExpandListener;
        if (onExpandListener != null) {
            onExpandListener.onExpand(this.mIsExpanded);
        }
    }

    public boolean isExpended() {
        return this.mIsExpanded;
    }

    public void setOnExpandListener(OnExpandListener onExpandListener) {
        this.mOnExpandListener = onExpandListener;
    }

    void refreshUi() {
        int i;
        int i2;
        ImageView imageView = this.mImageView;
        if (imageView != null) {
            if (this.mIsExpanded) {
                i2 = com.android.settings.R.drawable.ic_settings_expand_less;
            } else {
                i2 = com.android.settings.R.drawable.ic_settings_expand_more;
            }
            imageView.setImageResource(i2);
        }
        TextView textView = this.mTextView;
        if (textView != null) {
            textView.setText(getTitle());
            TextView textView2 = this.mTextView;
            Context context = textView2.getContext();
            if (this.mIsExpanded) {
                i = R.string.smart_battery_a11y_expand_label;
            } else {
                i = R.string.smart_battery_a11y_collapse_label;
            }
            textView2.setContentDescription(context.getString(i));
        }
    }
}
