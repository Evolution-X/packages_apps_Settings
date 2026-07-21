/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.settings.wifi.factory;

import android.content.Context;
import android.net.TetheringManager;
import android.net.wifi.WifiManager;
import android.print.PrintManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.android.settings.network.tether.TetheringRepository;
import com.android.settings.print.PrintRepository;
import com.android.settings.wifi.WifiDataUsageRepository;
import com.android.settings.wifi.details.WifiNetworkDetailsViewModel;
import com.android.settings.wifi.dpp.WifiDppQrCodeGeneratorFragment;
import com.android.settings.wifi.repository.DataUsageRepository;
import com.android.settings.wifi.repository.SavedNetworkRepository;
import com.android.settings.wifi.repository.SharedConnectivityRepository;
import com.android.settings.wifi.repository.WifiHotspotRepository;
import com.android.settings.wifi.tether.WifiHotspotSecurityViewModel;
import com.android.settings.wifi.tether.WifiHotspotSpeedViewModel;
import com.android.settings.wifi.tether.WifiTetherViewModel;

import com.google.android.settings.wifi.dpp.WifiDppQrCodeGeneratorFragmentGoogleImpl;

import org.jetbrains.annotations.NotNull;

/**
 * Wi-Fi Feature Provider
 */
public class WifiFeatureProvider {
    private static final String TAG = "WifiFeatureProvider";

    private final Context mAppContext;
    private WifiManager mWifiManager;
    private TetheringManager mTetheringManager;
    private PrintManager mPrintManager;
    private WifiVerboseLogging mWifiVerboseLogging;
    private WifiHotspotRepository mWifiHotspotRepository;
    private WifiDataUsageRepository mWifiDataUsageRepository;
    private SavedNetworkRepository mSavedNetworkRepository;
    private TetheringRepository mTetheringRepository;
    private SharedConnectivityRepository mSharedConnectivityRepository;
    private DataUsageRepository mDataUsageRepository;
    private PrintRepository mPrintRepository;

    public WifiFeatureProvider(@NonNull Context appContext) {
        mAppContext = appContext;
    }

    /**
     * Gets WifiManager
     */
    public WifiManager getWifiManager() {
        if (mWifiManager == null) {
            mWifiManager = mAppContext.getSystemService(WifiManager.class);
        }
        return mWifiManager;
    }

    /**
     * Gets TetheringManager
     */
    public TetheringManager getTetheringManager() {
        if (mTetheringManager == null) {
            mTetheringManager = mAppContext.getSystemService(TetheringManager.class);
            verboseLog(TAG, "getTetheringManager():" + mTetheringManager);
        }
        return mTetheringManager;
    }

    /**
     * Gets PrintManager
     */
    public PrintManager getPrintManager() {
        if (mPrintManager == null) {
            mPrintManager = mAppContext.getSystemService(PrintManager.class);
            verboseLog(TAG, "getPrintManager():" + mPrintManager);
        }
        return mPrintManager;
    }

    /**
     * Gets WifiVerboseLogging
     */
    public WifiVerboseLogging getWifiVerboseLogging() {
        if (mWifiVerboseLogging == null) {
            mWifiVerboseLogging = new WifiVerboseLogging(mAppContext, getWifiManager());
        }
        return mWifiVerboseLogging;
    }

    /**
     * Gets TetheringRepository
     */
    public TetheringRepository getTetheringRepository() {
        if (mTetheringRepository == null) {
            mTetheringRepository = new TetheringRepository(mAppContext, getTetheringManager());
            verboseLog(TAG, "getTetheringRepository():" + mTetheringRepository);
        }
        return mTetheringRepository;
    }

    /**
     * Gets WifiHotspotRepository
     */
    public WifiHotspotRepository getWifiHotspotRepository() {
        if (mWifiHotspotRepository == null) {
            mWifiHotspotRepository = new WifiHotspotRepository(mAppContext, getWifiManager(),
                    getTetheringManager());
            verboseLog(TAG, "getWifiHotspotRepository():" + mWifiHotspotRepository);
        }
        return mWifiHotspotRepository;
    }

    /**
     * Gets WifiDataUsageRepository
     */
    public WifiDataUsageRepository getWifiDataUsageRepository() {
        if (mWifiDataUsageRepository == null) {
            mWifiDataUsageRepository = new WifiDataUsageRepository(mAppContext);
            verboseLog(TAG, "getWifiDataUsageRepository():" + mWifiDataUsageRepository);
        }
        return mWifiDataUsageRepository;
    }

    /**
     * Gets SavedNetworkRepository
     */
    public SavedNetworkRepository getSavedNetworkRepository() {
        if (mSavedNetworkRepository == null) {
            mSavedNetworkRepository = new SavedNetworkRepository(mAppContext);
            verboseLog(TAG, "getSavedNetworkRepository():" + mSavedNetworkRepository);
        }
        return mSavedNetworkRepository;
    }

    /**
     * Gets SharedConnectivityRepository
     */
    public SharedConnectivityRepository getSharedConnectivityRepository() {
        if (mSharedConnectivityRepository == null) {
            mSharedConnectivityRepository = new SharedConnectivityRepository(mAppContext);
            verboseLog(TAG, "getSharedConnectivityRepository():" + mSharedConnectivityRepository);
        }
        return mSharedConnectivityRepository;
    }

    /**
     * Gets DataUsageRepository
     */
    public DataUsageRepository getDataUsageRepository() {
        if (mDataUsageRepository == null) {
            mDataUsageRepository = new DataUsageRepository(mAppContext);
            verboseLog(TAG, "getDataUsageRepository():" + mDataUsageRepository);
        }
        return mDataUsageRepository;
    }

    /**
     * Gets PrintRepository
     */
    public PrintRepository getPrintRepository() {
        if (mPrintRepository == null) {
            mPrintRepository = new PrintRepository(mAppContext);
            verboseLog(TAG, "getPrintRepository():" + mPrintRepository);
        }
        return mPrintRepository;
    }

    /**
     * Gets WifiTetherViewModel
     */
    public WifiTetherViewModel getWifiTetherViewModel(@NotNull ViewModelStoreOwner owner) {
        return new ViewModelProvider(owner).get(WifiTetherViewModel.class);
    }

    /**
     * Gets WifiHotspotSecurityViewModel
     */
    public WifiHotspotSecurityViewModel getWifiHotspotSecurityViewModel(
            @NotNull ViewModelStoreOwner owner) {
        WifiHotspotSecurityViewModel viewModel =
                new ViewModelProvider(owner).get(WifiHotspotSecurityViewModel.class);
        verboseLog(TAG, "getWifiHotspotSecurityViewModel():" + viewModel);
        return viewModel;
    }

    /**
     * Gets WifiHotspotSpeedViewModel
     */
    public WifiHotspotSpeedViewModel getWifiHotspotSpeedViewModel(
            @NotNull ViewModelStoreOwner owner) {
        WifiHotspotSpeedViewModel viewModel =
                new ViewModelProvider(owner).get(WifiHotspotSpeedViewModel.class);
        verboseLog(TAG, "getWifiHotspotSpeedViewModel():" + viewModel);
        return viewModel;
    }

    /**
     * Gets WifiNetworkDetailsViewModel
     */
    public WifiNetworkDetailsViewModel getWifiNetworkDetailsViewModel(
            @NotNull ViewModelStoreOwner owner) {
        WifiNetworkDetailsViewModel viewModel =
                new ViewModelProvider(owner).get(WifiNetworkDetailsViewModel.class);
        verboseLog(TAG, "getWifiNetworkDetailsViewModel():" + viewModel);
        return viewModel;
    }

    /**
     * Gets an instance of WifiDppQrCodeGeneratorFragment
     */
    public WifiDppQrCodeGeneratorFragment getWifiDppQrCodeGeneratorFragment() {
        WifiDppQrCodeGeneratorFragment fragment = new WifiDppQrCodeGeneratorFragmentGoogleImpl();
        verboseLog(TAG, "getWifiDppQrCodeGeneratorFragment():" + fragment);
        return fragment;
    }

    /**
     * Send a {@link Log#VERBOSE} log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public void verboseLog(@Nullable String tag, @NonNull String msg) {
        getWifiVerboseLogging().log(tag, msg);
    }
}

