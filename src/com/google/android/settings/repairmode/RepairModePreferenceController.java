package com.google.android.settings.repairmode;

import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

import com.android.internal.widget.LockPatternUtils;

public class RepairModePreferenceController extends BasePreferenceController {

    public RepairModePreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        if (!LockPatternUtils.isRepairModeSupported(mContext)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        if (mContext.getSystemService(UserManager.class).hasUserRestriction(
                UserManager.DISALLOW_SAFE_BOOT)) {
            return DISABLED_FOR_USER;
        }
        return LockPatternUtils.isRepairModeActive(mContext)
                || LockPatternUtils.canUserEnterRepairMode(mContext, getCurrentUserInfo())
                ? AVAILABLE
                : DISABLED_FOR_USER;
    }

    @Override
    public CharSequence getSummary() {
        final boolean repairModeActive = LockPatternUtils.isRepairModeActive(mContext);
        if (repairModeActive) {
            return mContext.getString(R.string.repair_mode_active_summary);
        }
        return mContext.getString(R.string.repair_mode_summary);
    }

    private UserInfo getCurrentUserInfo() {
        return mContext.getSystemService(UserManager.class).getUserInfo(
                UserHandle.myUserId());
    }
}
