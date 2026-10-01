/*
 * Copyright (C) 2026 The Evolution X Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.network.telephony;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.ListPreferenceDialogFragmentCompat;

import com.android.settings.CustomListPreference;
import com.android.settings.R;

/**
 * Lifecycle-safe two-line preferred-network-mode selector.
 *
 * <p>The primary line stays intentionally simple (2G/3G/4G/5G combinations). The secondary line
 * exposes the exact radio families and an optional status line explains runtime restrictions
 * without hiding hardware-supported modes.</p>
 */
public class NetworkModePreference extends CustomListPreference {

    private CharSequence[] mEntrySections = new CharSequence[0];
    private CharSequence[] mEntryDetails = new CharSequence[0];
    private CharSequence[] mEntryStatuses = new CharSequence[0];
    private boolean[] mEntrySelectable = new boolean[0];

    public NetworkModePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NetworkModePreference(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setEntrySections(@Nullable CharSequence[] entrySections) {
        mEntrySections = entrySections != null ? entrySections : new CharSequence[0];
    }

    public void setEntryDetails(@Nullable CharSequence[] entryDetails) {
        mEntryDetails = entryDetails != null ? entryDetails : new CharSequence[0];
    }

    public void setEntryStatuses(@Nullable CharSequence[] entryStatuses) {
        mEntryStatuses = entryStatuses != null ? entryStatuses : new CharSequence[0];
    }

    public void setEntrySelectable(@Nullable boolean[] entrySelectable) {
        mEntrySelectable = entrySelectable != null ? entrySelectable : new boolean[0];
    }

    @VisibleForTesting
    CharSequence[] getEntrySectionsForTest() {
        return mEntrySections.clone();
    }

    @VisibleForTesting
    CharSequence[] getEntryStatusesForTest() {
        return mEntryStatuses.clone();
    }

    @Override
    protected void onPrepareDialogBuilder(
            Builder builder, DialogInterface.OnClickListener listener) {
        final CharSequence[] entries = getEntries();
        final CharSequence[] values = getEntryValues();
        if (entries == null || values == null || entries.length == 0
                || entries.length != values.length) {
            return;
        }

        builder.setSingleChoiceItems(
                new NetworkModeAdapter(getContext(), entries),
                findIndexOfValue(getValue()),
                listener);
    }

    /** Dismisses only this preference's managed dialog, leaving unrelated dialogs untouched. */
    public static void dismissDialog(@Nullable FragmentManager fragmentManager) {
        if (fragmentManager == null) {
            return;
        }
        for (Fragment fragment : fragmentManager.getFragments()) {
            if (!(fragment instanceof ListPreferenceDialogFragmentCompat)) {
                continue;
            }
            final ListPreferenceDialogFragmentCompat listFragment =
                    (ListPreferenceDialogFragmentCompat) fragment;
            if (listFragment.getPreference() instanceof NetworkModePreference) {
                listFragment.dismiss();
            }
        }
    }

    private final class NetworkModeAdapter extends BaseAdapter {
        private final CharSequence[] mEntries;
        private final LayoutInflater mInflater;

        NetworkModeAdapter(Context context, CharSequence[] entries) {
            mEntries = entries;
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return mEntries.length;
        }

        @Override
        public CharSequence getItem(int position) {
            return mEntries[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean areAllItemsEnabled() {
            for (int index = 0; index < getCount(); index++) {
                if (!isEntrySelectable(index)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean isEnabled(int position) {
            return isEntrySelectable(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final CheckedTextView view;
            if (convertView instanceof CheckedTextView) {
                view = (CheckedTextView) convertView;
            } else {
                view = (CheckedTextView) mInflater.inflate(
                        R.layout.network_mode_dialog_item,
                        parent,
                        false);
            }

            view.setText(buildRowText(position, getItem(position)), TextView.BufferType.SPANNABLE);
            view.setEnabled(isEnabled(position));
            view.setContentDescription(buildContentDescription(position, getItem(position)));
            return view;
        }
    }

    private CharSequence buildRowText(int index, CharSequence title) {
        final SpannableStringBuilder text = new SpannableStringBuilder();

        final CharSequence section = getAt(mEntrySections, index);
        if (section != null && section.length() > 0) {
            final int sectionStart = text.length();
            text.append(section).append('\n');
            text.setSpan(
                    new RelativeSizeSpan(0.78f),
                    sectionStart,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    sectionStart,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        final int titleStart = text.length();
        text.append(title);
        text.setSpan(
                new StyleSpan(Typeface.BOLD),
                titleStart,
                text.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        final CharSequence detail = getAt(mEntryDetails, index);
        if (detail != null && detail.length() > 0) {
            final int start = text.length();
            text.append('\n').append(detail);
            text.setSpan(
                    new RelativeSizeSpan(0.86f),
                    start,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        final CharSequence status = getAt(mEntryStatuses, index);
        if (status != null && status.length() > 0) {
            final int start = text.length();
            text.append('\n').append(status);
            text.setSpan(
                    new RelativeSizeSpan(0.82f),
                    start,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(
                    new StyleSpan(Typeface.ITALIC),
                    start,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    private CharSequence buildContentDescription(int index, CharSequence title) {
        final SpannableStringBuilder description = new SpannableStringBuilder();
        final CharSequence section = getAt(mEntrySections, index);
        if (section != null && section.length() > 0) {
            description.append(section).append(", ");
        }
        description.append(title);

        final CharSequence detail = getAt(mEntryDetails, index);
        if (detail != null && detail.length() > 0) {
            description.append(", ").append(detail);
        }

        final CharSequence status = getAt(mEntryStatuses, index);
        if (status != null && status.length() > 0) {
            description.append(", ").append(status);
        }
        return description;
    }

    private boolean isEntrySelectable(int index) {
        return index < 0 || index >= mEntrySelectable.length || mEntrySelectable[index];
    }

    @Nullable
    private static CharSequence getAt(CharSequence[] values, int index) {
        return index >= 0 && index < values.length ? values[index] : null;
    }
}
