package com.google.android.settings.wifi.dpp

import android.os.Bundle
import android.view.View
import com.android.settings.R
import com.android.settings.wifi.dpp.WifiDppQrCodeGeneratorFragment
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class WifiDppQrCodeGeneratorFragmentGoogleImpl : WifiDppQrCodeGeneratorFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.qrcode_view)?.visibility = View.GONE

        view.findViewById<View>(R.id.qr_code_fragment_container_view)?.visibility = View.VISIBLE
    }

    override fun setQrCode() {
        val fragmentManager = childFragmentManager
        val containerId = R.id.qr_code_fragment_container_view

        if (fragmentManager.findFragmentById(containerId) == null) {
            fragmentManager
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(containerId, MaterialShapeQrFragment())
                .commitNow()
        }

        val qrFragment = fragmentManager.findFragmentById(containerId) as MaterialShapeQrFragment
        val content = mQrCode ?: error("mQrCode must not be null")

        qrFragment.updateQrCodeContent(content, ErrorCorrectionLevel.L)
    }
}
