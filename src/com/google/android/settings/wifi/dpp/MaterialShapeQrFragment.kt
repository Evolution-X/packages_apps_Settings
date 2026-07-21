package com.google.android.settings.wifi.dpp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.VectorDrawable
import android.os.Bundle
import android.os.Trace
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.android.settings.R
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.QRCode
import kotlin.random.Random

class MaterialShapeQrFragment : Fragment() {

    companion object {
        @JvmStatic private var disableAnimationForTesting: Boolean = false

        fun createQrCode(content: String, ecLevel: ErrorCorrectionLevel): QRCode {
            val hints =
                java.util.EnumMap<com.google.zxing.EncodeHintType, Any>(
                    com.google.zxing.EncodeHintType::class.java
                )
            val canEncodeAsLatin1 = Charsets.ISO_8859_1.newEncoder().canEncode(content)
            if (!canEncodeAsLatin1) {
                hints[com.google.zxing.EncodeHintType.CHARACTER_SET] = Charsets.UTF_8.name()
            }
            return com.google.zxing.qrcode.encoder.Encoder.encode(content, ecLevel, hints)
        }

        fun randomRotationForSquareShape(): Int {
            return Random.nextInt() % 4
        }
    }

    private var arrayOf1x1Shapes: Array<VectorDrawable> = emptyArray()
    private var arrayOf1x1SemiCircleShapes: Array<VectorDrawable> = emptyArray()
    private var arrayOf2x2Shapes: Array<VectorDrawable> = emptyArray()
    private var arrayOf3x3Shapes: Array<VectorDrawable> = emptyArray()
    private var arrayOf7x7Shapes: Array<VectorDrawable> = emptyArray()
    private var arrayOfHorizontalBarShapes: Array<VectorDrawable> = emptyArray()
    private var arrayOfHorizontalHalfCapsuleBarShapes: Array<VectorDrawable> = emptyArray()
    private var arrayOfVerticalBarShapes: Array<VectorDrawable> = emptyArray()
    private var arrayOfFinderPatternCenterShapes: Array<VectorDrawable> = emptyArray()

    private var foregroundColorPrimary: Int = -0xff0100
    private var foregroundColorSecondary: Int = -0xffff01
    private var foregroundColorAccent: Int = -0x10000
    private var mainForegroundColorArray: Array<Int> =
        arrayOf(foregroundColorPrimary, foregroundColorSecondary)

    private var backgroundShapeColor: Int = foregroundColorAccent
    private var backgroundDotColor1: Int = foregroundColorAccent
    private var backgroundDotColor2: Int = foregroundColorAccent
    private var backgroundDotColor3: Int = foregroundColorAccent
    private var mainBackgroundColorArray: Array<Int> =
        arrayOf(backgroundDotColor1, backgroundDotColor2, backgroundDotColor3)

    private var hasCreated: Array<Array<Boolean>> = arrayOf(arrayOf(false))

    private var finderPatternShapeList: MutableList<MaterialShapeRenderer> = ArrayList()
    private var dataModuleShapeList: MutableList<MaterialShapeRenderer> = ArrayList()
    private var backgroundModuleShapeList: MutableList<MaterialShapeRenderer> = ArrayList()
    private var finderPatternCenterShapeIndex: Int = 0

    lateinit var qrcode: QRCode
        private set

    private var qrCodeContent: String? = null
    private var qrCodeEcLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.L
    private var qrcodeLineCount: Int = 0
    private var moduleSize: Int = 29
    private var qrcodeBitmapSize: Int = 0
    private var qrStartTime: Long = 0L
    private var hasFinalDataImage: Boolean = false
    private var isMotionPaused: Boolean = false

    private lateinit var frameLayoutView: View
    private var hostContext: Context? = null
    private lateinit var qrCodeViewForBackground: LottieAnimationView
    private lateinit var qrCodeViewForFinderPatterns: ImageView
    private lateinit var qrCodeViewForNonFinderPatterns: ImageView
    private lateinit var qrCodeViewForStandard: ImageView
    private lateinit var bitmapForFinderPatterns: Bitmap
    private lateinit var bitmapForNonFinderPatterns: Bitmap

    private val onDrawListener = ViewTreeObserver.OnDrawListener { onDrawCallback() }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Trace.beginSection("onAttach")

        val s1Circle = loadVectorDrawable(context, R.drawable.qrcode_square_s1_circle)
        val s1Drop = loadVectorDrawable(context, R.drawable.qrcode_square_s1_drop)
        val s1SemiCircle = loadVectorDrawable(context, R.drawable.qrcode_square_s1_semi_circle)
        val s1Square = loadVectorDrawable(context, R.drawable.qrcode_square_s1_square)
        arrayOf1x1Shapes = arrayOf(s1Circle, s1Drop, s1Square)
        arrayOf1x1SemiCircleShapes = arrayOf(s1SemiCircle)

        arrayOf2x2Shapes =
            arrayOf(
                loadVectorDrawable(context, R.drawable.qrcode_square_s2_circle),
                loadVectorDrawable(context, R.drawable.qrcode_square_s2_clover),
                loadVectorDrawable(context, R.drawable.qrcode_square_s2_hexagonal),
                loadVectorDrawable(context, R.drawable.qrcode_square_s2_meteroid),
                loadVectorDrawable(context, R.drawable.qrcode_square_s2_wiggle_star),
            )

        val s3Circle = loadVectorDrawable(context, R.drawable.qrcode_square_s3_circle)
        val s3Clover = loadVectorDrawable(context, R.drawable.qrcode_square_s3_clover)
        val s3Hexagonal = loadVectorDrawable(context, R.drawable.qrcode_square_s3_hexagonal)
        val s3Meteroid = loadVectorDrawable(context, R.drawable.qrcode_square_s3_meteroid)
        val s3WiggleStar = loadVectorDrawable(context, R.drawable.qrcode_square_s3_wiggle_star)
        arrayOf3x3Shapes = arrayOf(s3Circle, s3Clover, s3Hexagonal, s3Meteroid, s3WiggleStar)

        arrayOfFinderPatternCenterShapes =
            arrayOf(s3Hexagonal, s3Meteroid, s3WiggleStar).also { it.shuffle() }
        finderPatternCenterShapeIndex = 0

        arrayOf7x7Shapes = arrayOf(loadVectorDrawable(context, R.drawable.qrcode_square_s7_ring))

        val hBarS2 = loadVectorDrawable(context, R.drawable.qrcode_hor_bar_s2_capsule)
        val hBarS3 = loadVectorDrawable(context, R.drawable.qrcode_hor_bar_s3_capsule)
        val hHalfS2 = loadVectorDrawable(context, R.drawable.qrcode_hor_bar_s2_half_capsule)
        val hHalfS3 = loadVectorDrawable(context, R.drawable.qrcode_hor_bar_s3_half_capsule)
        val vBarS2 = loadVectorDrawable(context, R.drawable.qrcode_ver_bar_s2_capsule)
        val vBarS3 = loadVectorDrawable(context, R.drawable.qrcode_ver_bar_s3_capsule)
        arrayOfHorizontalBarShapes = arrayOf(hBarS2, hBarS3)
        arrayOfHorizontalHalfCapsuleBarShapes = arrayOf(hHalfS2, hHalfS3)
        arrayOfVerticalBarShapes = arrayOf(vBarS2, vBarS3)

        loadDynamicColors(context)
        hostContext = context

        Trace.endSection()
    }

    private fun loadVectorDrawable(
        context: Context,
        @androidx.annotation.DrawableRes resId: Int,
    ): VectorDrawable {
        return context.getDrawable(resId) as VectorDrawable
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Trace.beginSection("onCreateView")

        val view = inflater.inflate(R.layout.material_shape_qr_fragment, container, false)
        frameLayoutView = view.findViewById(R.id.material_shape_qr_fragment)

        if (!disableAnimationForTesting) {
            frameLayoutView.viewTreeObserver.addOnDrawListener(onDrawListener)
        }

        frameLayoutView.setOnClickListener { onQrCodeClicked() }
        androidx.core.view.ViewCompat.setAccessibilityDelegate(
            frameLayoutView,
            qrCodeAccessibilityDelegate(),
        )

        qrCodeViewForNonFinderPatterns =
            view.findViewById(R.id.qr_code_img_view_for_non_finder_patterns)

        qrCodeViewForFinderPatterns = view.findViewById(R.id.qr_code_img_view_for_finder_patterns)
        val contentDescription = getString(R.string.qr_code_content_description)
        qrCodeViewForFinderPatterns.contentDescription = contentDescription

        qrCodeViewForBackground = view.findViewById(R.id.qr_code_background_view)
        applyLottieDynamicColor()
        qrCodeViewForBackground.playAnimation()

        qrCodeViewForStandard = view.findViewById(R.id.standard_qr_code_img_view)
        qrCodeViewForStandard.contentDescription = contentDescription

        Trace.endSection()
        return view
    }

    private fun onQrCodeClicked() {
        val content = qrCodeContent ?: return

        isMotionPaused = true
        qrCodeViewForBackground.visibility = View.GONE
        qrCodeViewForNonFinderPatterns.visibility = View.GONE
        qrCodeViewForFinderPatterns.visibility = View.GONE
        qrCodeViewForStandard.visibility = View.VISIBLE

        val pixelSize = 8 * (qrcodeLineCount + 8)
        val bitmap = com.android.settingslib.qrcode.QrCodeGenerator.encodeQrCode(content, pixelSize)
        qrCodeViewForStandard.setImageBitmap(bitmap)
    }

    private fun qrCodeAccessibilityDelegate(): androidx.core.view.AccessibilityDelegateCompat {
        return object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    androidx.core.view.accessibility.AccessibilityNodeInfoCompat
                        .AccessibilityActionCompat(
                            androidx.core.view.accessibility.AccessibilityNodeInfoCompat
                                .ACTION_CLICK,
                            getString(R.string.wifi_qr_code_animation_control),
                        )
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        frameLayoutView.viewTreeObserver.removeOnDrawListener(onDrawListener)
    }

    override fun onDetach() {
        hostContext = null
        arrayOf1x1Shapes = emptyArray()
        arrayOf1x1SemiCircleShapes = emptyArray()
        arrayOf2x2Shapes = emptyArray()
        arrayOf3x3Shapes = emptyArray()
        arrayOfFinderPatternCenterShapes = emptyArray()
        arrayOf7x7Shapes = emptyArray()
        arrayOfHorizontalBarShapes = emptyArray()
        arrayOfVerticalBarShapes = emptyArray()
        arrayOfHorizontalHalfCapsuleBarShapes = emptyArray()
        super.onDetach()
    }

    fun updateQrCodeContent(qrCodeContent: String, qrCodeEcLevel: ErrorCorrectionLevel) {
        this.qrCodeContent = qrCodeContent
        this.qrCodeEcLevel = qrCodeEcLevel

        if (hostContext != null) {
            Trace.beginSection("createMaterialShapeQRCode")
            createMaterialShapeQRCode(qrCodeContent, qrCodeEcLevel)
            Trace.endSection()
        }
    }

    private fun createMaterialShapeQRCode(content: String, ecLevel: ErrorCorrectionLevel) {
        val context = hostContext ?: run { error("Host context cannot be null") }

        qrcode = createQrCode(content, ecLevel)
        qrStartTime = System.currentTimeMillis()
        hasFinalDataImage = false
        qrcodeLineCount = qrcode.matrix.width

        val metrics = context.resources.displayMetrics
        val shortestScreenSide = minOf(metrics.widthPixels, metrics.heightPixels)
        val rawModuleSize = minOf(shortestScreenSide, 1200) / qrcodeLineCount
        moduleSize = minOf(rawModuleSize, 29)
        qrcodeBitmapSize = moduleSize * qrcodeLineCount

        hasCreated = Array(qrcodeLineCount) { Array(qrcodeLineCount) { false } }

        finderPatternShapeList.clear()
        dataModuleShapeList.clear()

        bitmapForNonFinderPatterns =
            Bitmap.createBitmap(qrcodeBitmapSize, qrcodeBitmapSize, Bitmap.Config.ARGB_8888)
        bitmapForFinderPatterns =
            Bitmap.createBitmap(qrcodeBitmapSize, qrcodeBitmapSize, Bitmap.Config.ARGB_8888)

        val finderColor = getColorForFinderPattern()
        val n = qrcodeLineCount

        createRendererForShape(0, 0, 7, 7, finderPatternShapeList, randomSquare(7), finderColor) {
            it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.EmphasizedZoomIn)
            it.setStartDelay(833)
            it.setDuration(834)
        }
        createRendererForShape(
            n - 7,
            0,
            7,
            7,
            finderPatternShapeList,
            randomSquare(7),
            finderColor,
        ) {
            it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.EmphasizedZoomIn)
            it.setStartDelay(833)
            it.setDuration(834)
        }
        createRendererForShape(
            0,
            n - 7,
            7,
            7,
            finderPatternShapeList,
            randomSquare(7),
            finderColor,
        ) {
            it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.EmphasizedZoomIn)
            it.setStartDelay(833)
            it.setDuration(834)
        }
        createRendererForShape(
            2,
            2,
            3,
            3,
            finderPatternShapeList,
            nextFinderPatternCenter(),
            finderColor,
        ) {
            it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.RotateEmphasizedZoomIn)
            it.setStartDelay(1167)
            it.setDuration(1667)
        }
        createRendererForShape(
            n - 5,
            2,
            3,
            3,
            finderPatternShapeList,
            nextFinderPatternCenter(),
            finderColor,
        ) {
            it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.RotateEmphasizedZoomIn)
            it.setStartDelay(1167)
            it.setDuration(1667)
        }
        createRendererForShape(
            2,
            n - 5,
            3,
            3,
            finderPatternShapeList,
            nextFinderPatternCenter(),
            finderColor,
        ) {
            it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.RotateEmphasizedZoomIn)
            it.setStartDelay(1167)
            it.setDuration(1667)
        }

        searchAndCreateLargeSquareShapes(3, dataModuleShapeList)
        searchAndCreateLargeSquareShapes(2, dataModuleShapeList)
        searchAndCreateHorizontalBars(4, dataModuleShapeList)
        searchAndCreateBars(3, dataModuleShapeList)
        searchAndCreateBars(2, dataModuleShapeList)
        searchAndCreateSmallForegroundSquareShapes(dataModuleShapeList)

        searchAndCreateSmallBackgroundSquareShapes(backgroundModuleShapeList)
    }

    private fun createRendererForShape(
        col: Int,
        row: Int,
        w: Int,
        h: Int,
        shapeList: MutableList<MaterialShapeRenderer>,
        drawable: VectorDrawable,
        color: Int,
        configure: (MaterialShapeRenderer) -> Unit,
    ) {
        val bounds =
            RectF(
                (col * moduleSize).toFloat(),
                (row * moduleSize).toFloat(),
                ((col + w) * moduleSize).toFloat(),
                ((row + h) * moduleSize).toFloat(),
            )
        val paint =
            Paint().apply { colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }
        val renderer = MaterialShapeRenderer(drawable, bounds, paint)
        renderer.setStartDelay(calculateStartDelay(col, row, w, h))

        configure(renderer)
        shapeList.add(renderer)
        markAsCreated(col, row, w, h)
    }

    private fun createRendererForShape(
        col: Int,
        row: Int,
        w: Int,
        h: Int,
        shapeList: MutableList<MaterialShapeRenderer>,
        drawable: VectorDrawable,
        configure: (MaterialShapeRenderer) -> Unit,
    ) {
        createRendererForShape(
            col,
            row,
            w,
            h,
            shapeList,
            drawable,
            randomForegroundColor(w, h),
            configure,
        )
    }

    private fun createHorizontalBar(
        col: Int,
        row: Int,
        barLen: Int,
        shapeList: MutableList<MaterialShapeRenderer>,
    ) {
        require(barLen <= 4) { "barLen must be <= 4" }

        fun plainBar() {
            createRendererForShape(col, row, barLen, 1, shapeList, randomHorizontalBar(barLen)) {
                it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.SpringZoomIn)
                it.setStartDelay(calculateStartDelay(col, row, barLen, 1))
            }
        }

        if (barLen <= 2 || (barLen == 3 && Random.nextFloat() < 0.5f)) {
            plainBar()
            return
        }
        if (Random.nextFloat() > 0.5f) {
            createRendererForShape(
                col,
                row,
                1,
                1,
                shapeList,
                getSemiCircle(),
                randomForegroundColor(1, 1),
            ) {
                it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.ZoomIn)
                it.setStartDelay(calculateStartDelay(col, row, 1, 1))
                it.setSkipStartProgress(0.3f)
            }
            createRendererForShape(
                col + 1,
                row,
                barLen - 1,
                1,
                shapeList,
                randomHorizontalHalfCapsuleBar(barLen - 1),
            ) {
                it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.SpringZoomIn)
                it.setStartDelay(calculateStartDelay(col + 1, row, barLen - 1, 1))
            }
        } else {
            createRendererForShape(
                col,
                row,
                barLen - 1,
                1,
                shapeList,
                randomHorizontalHalfCapsuleBar(barLen - 1),
            ) {
                it.setInitialRotation(2)
                it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.SpringZoomIn)
                it.setStartDelay(calculateStartDelay(col, row, barLen - 1, 1))
            }
            createRendererForShape(
                col + barLen - 1,
                row,
                1,
                1,
                shapeList,
                getSemiCircle(),
                randomForegroundColor(1, 1),
            ) {
                it.setInitialRotation(2)
                it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.ZoomIn)
                it.setStartDelay(calculateStartDelay(col + barLen - 1, row, 1, 1))
            }
        }
    }

    private fun createSingleVerticalBar(
        col: Int,
        row: Int,
        barLen: Int,
        shapeList: MutableList<MaterialShapeRenderer>,
    ) {
        createRendererForShape(col, row, 1, barLen, shapeList, randomVerticalBar(barLen)) {
            it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.SpringZoomIn)
            it.setStartDelay(calculateStartDelay(col, row, 1, barLen))
        }
    }

    private fun markAsCreated(col: Int, row: Int, w: Int, h: Int) {
        for (dx in 0 until w) {
            for (dy in 0 until h) {
                hasCreated[col + dx][row + dy] = true
            }
        }
    }

    private fun isForeground(col: Int, row: Int): Boolean {
        return (qrcode.matrix.get(col, row).toInt() and 0xF) == 1
    }

    private fun searchAndCreateLargeSquareShapes(
        size: Int,
        shapeList: MutableList<MaterialShapeRenderer>,
    ) {
        for (row in 0 until qrcodeLineCount - size + 1) {
            for (col in 0 until qrcodeLineCount - size + 1) {
                if (blockIsFreeAndForeground(col, row, size, size)) {
                    createRendererForShape(col, row, size, size, shapeList, randomSquare(size)) {
                        it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.SpringZoomIn)
                        it.setStartDelay(calculateStartDelay(col, row, size, size))
                        it.setInitialRotation(Companion.randomRotationForSquareShape())
                    }
                }
            }
        }
    }

    private fun searchAndCreateHorizontalBars(
        len: Int,
        shapeList: MutableList<MaterialShapeRenderer>,
    ) {
        require(len <= 4) { "barLen must be <= 4" }
        for (row in 0 until qrcodeLineCount) {
            for (col in 0 until qrcodeLineCount - len + 1) {
                if (blockIsFreeAndForeground(col, row, len, 1)) {
                    createHorizontalBar(col, row, len, shapeList)
                }
            }
        }
    }

    private fun searchAndCreateBars(len: Int, shapeList: MutableList<MaterialShapeRenderer>) {
        val horizontalFirst =
            listOf<(Int, Int, Int, MutableList<MaterialShapeRenderer>) -> Unit>(
                ::tryFindingHorizontalBar,
                ::tryFindingVerticalBar,
            )
        val verticalFirst = horizontalFirst.reversed()

        for (row in 0 until qrcodeLineCount) {
            for (col in 0 until qrcodeLineCount) {
                if (!hasCreated[col][row] && isForeground(col, row)) {
                    val order = if (Random.nextFloat() < 0.5f) horizontalFirst else verticalFirst
                    for (tryFind in order) {
                        tryFind(col, row, len, shapeList)
                    }
                }
            }
        }
    }

    private fun tryFindingHorizontalBar(
        col: Int,
        row: Int,
        len: Int,
        shapeList: MutableList<MaterialShapeRenderer>,
    ) {
        require(len <= 4) { "barLen must be <= 4" }
        if (col + len > qrcodeLineCount) return
        for (k in 0 until len) {
            if (hasCreated[col + k][row] || !isForeground(col + k, row)) return
        }
        createHorizontalBar(col, row, len, shapeList)
    }

    private fun tryFindingVerticalBar(
        col: Int,
        row: Int,
        len: Int,
        shapeList: MutableList<MaterialShapeRenderer>,
    ) {
        if (row + len > qrcodeLineCount) return
        for (k in 0 until len) {
            if (!isForeground(col, row + k) || hasCreated[col][row + k]) return
        }
        createSingleVerticalBar(col, row, len, shapeList)
    }

    private fun searchAndCreateSmallForegroundSquareShapes(
        shapeList: MutableList<MaterialShapeRenderer>
    ) {
        for (row in 0 until qrcodeLineCount) {
            for (col in 0 until qrcodeLineCount) {
                if (!hasCreated[col][row] && isForeground(col, row)) {
                    createRendererForShape(col, row, 1, 1, shapeList, randomSquare(1)) {
                        it.setAnimationStyle(MaterialShapeRenderer.EntryAnimationStyle.ZoomIn)
                        it.setStartDelay(calculateStartDelay(col, row, 1, 1))
                        it.setSkipStartProgress(0.3f)
                    }
                }
            }
        }
    }

    private fun searchAndCreateSmallBackgroundSquareShapes(
        shapeList: MutableList<MaterialShapeRenderer>
    ) {
        for (row in 0 until qrcodeLineCount) {
            for (col in 0 until qrcodeLineCount) {
                if (!hasCreated[col][row] && !isForeground(col, row)) {
                    createRendererForShape(col, row, 1, 1, shapeList, randomSquare(1)) {
                        it.setAnimationStyle(
                            MaterialShapeRenderer.EntryAnimationStyle.EmphasizedZoomIn
                        )
                        val ratio = calculateRatioToCenter(col, row, 1, 1)
                        it.setStartDelay((1250f * ratio).toLong() + 0x53) // +83ms
                        it.setSkipStartProgress(0.3f)
                        val paint =
                            Paint().apply {
                                colorFilter =
                                    PorterDuffColorFilter(
                                        randomBackgroundColor(),
                                        PorterDuff.Mode.SRC_IN,
                                    )
                            }
                        it.setPaint(paint)
                    }
                }
            }
        }
    }

    private fun blockIsFreeAndForeground(col: Int, row: Int, w: Int, h: Int): Boolean {
        for (dx in 0 until w) {
            for (dy in 0 until h) {
                val c = col + dx
                val r = row + dy
                if (c >= qrcodeLineCount || r >= qrcodeLineCount) return false
                if (hasCreated[c][r] || !isForeground(c, r)) return false
            }
        }
        return true
    }

    private fun randomSquare(size: Int): VectorDrawable =
        when (size) {
            1 -> arrayOf1x1Shapes.random()
            2 -> arrayOf2x2Shapes.random()
            3 -> arrayOf3x3Shapes.random()
            7 -> arrayOf7x7Shapes.first()
            else -> {
                error("Unsupported square shape: $size")
            }
        }

    private fun randomHorizontalBar(len: Int): VectorDrawable = arrayOfHorizontalBarShapes[len - 2]

    private fun randomVerticalBar(len: Int): VectorDrawable = arrayOfVerticalBarShapes[len - 2]

    private fun randomHorizontalHalfCapsuleBar(len: Int): VectorDrawable =
        arrayOfHorizontalHalfCapsuleBarShapes[len - 2]

    private fun randomForegroundColor(w: Int, h: Int): Int {
        if (h == 1) {
            val accentChance = listOf(0.2f, 0.04f, 0.01f, 0f).getOrElse(w - 1) { 0f }
            if (Random.nextFloat() <= accentChance) {
                return foregroundColorAccent
            }
        }
        return mainForegroundColorArray.random()
    }

    private fun randomBackgroundColor(): Int = mainBackgroundColorArray.random()

    private fun getColorForFinderPattern(): Int = foregroundColorPrimary

    private fun getSemiCircle(): VectorDrawable = arrayOf1x1SemiCircleShapes.first()

    private fun nextFinderPatternCenter(): VectorDrawable {
        val shape =
            arrayOfFinderPatternCenterShapes[
                finderPatternCenterShapeIndex % arrayOfFinderPatternCenterShapes.size]
        finderPatternCenterShapeIndex++
        return shape
    }

    private fun calculateRatioToCenter(col: Int, row: Int, w: Int, h: Int): Float {
        val half = qrcodeLineCount / 2f
        val maxDist = 1.414f * half
        val dx = (col + w / 2f) - half
        val dy = (row + h / 2f) - half
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat() / maxDist
    }

    private fun calculateStartDelay(col: Int, row: Int, w: Int, h: Int): Long {
        val base = (calculateRatioToCenter(col, row, w, h) * 1000f).toLong()
        val jitter = if (w == 1 && h == 1) 0L else Random.nextLong(0x190) // 0..400ms
        return base + jitter
    }

    private fun onDrawCallback() {
        if (qrcodeLineCount == 0 || isMotionPaused) return
        Trace.beginSection("onDraw")

        val elapsed = System.currentTimeMillis() - qrStartTime
        if (elapsed <= 3000L) {
            drawModules(elapsed)
        } else if (!hasFinalDataImage) {
            drawModules(elapsed)
            hasFinalDataImage = true
        }
        drawFinderPatterns(elapsed)
        drawAnimationBackground(elapsed)

        Trace.endSection()
    }

    private fun drawFinderPatterns(elapsedMs: Long) {
        bitmapForFinderPatterns.eraseColor(0)
        val canvas = Canvas(bitmapForFinderPatterns)
        drawShapeListOnCanvas(finderPatternShapeList, canvas, elapsedMs)
        qrCodeViewForFinderPatterns.setImageBitmap(bitmapForFinderPatterns)
    }

    private fun drawModules(elapsedMs: Long) {
        bitmapForNonFinderPatterns.eraseColor(0)
        val canvas = Canvas(bitmapForNonFinderPatterns)
        drawShapeListOnCanvas(dataModuleShapeList, canvas, elapsedMs)
        drawShapeListOnCanvas(backgroundModuleShapeList, canvas, elapsedMs)
        qrCodeViewForNonFinderPatterns.setImageBitmap(bitmapForNonFinderPatterns)
    }

    private fun drawShapeListOnCanvas(
        shapes: List<MaterialShapeRenderer>,
        canvas: Canvas,
        elapsedMs: Long,
    ) {
        for (shape in shapes) {
            shape.draw(canvas, elapsedMs)
        }
    }

    private fun drawAnimationBackground(elapsedMs: Long) {
        if (elapsedMs < 1100) {
            val scale = calculateAnimationBackgroundScale(elapsedMs)
            qrCodeViewForBackground.scaleX = scale
            qrCodeViewForBackground.scaleY = scale
        }
        if (elapsedMs <= 600) {
            val alpha = calculateAnimationBackgroundAlpha(elapsedMs)
            val color = ColorUtils.setAlphaComponent(backgroundShapeColor, (alpha * 255f).toInt())
            qrCodeViewForBackground.addValueCallback(
                KeyPath("**", ".bg", "**"),
                LottieProperty.COLOR_FILTER,
            ) {
                PorterDuffColorFilter(color, PorterDuff.Mode.SRC)
            }
        }
    }

    private fun calculateAnimationBackgroundScale(elapsedMs: Long): Float {
        if (elapsedMs < 250) return 0f
        if (elapsedMs >= 1083) return 1f
        val eased = EmphasizedInterpolator.getInterpolation((elapsedMs - 250) / 833f)
        return eased * 0.2f + 0.8f
    }

    private fun calculateAnimationBackgroundAlpha(elapsedMs: Long): Float {
        if (elapsedMs < 250) return 0f
        if (elapsedMs < 583) return (elapsedMs - 250) / 333f
        return 1f
    }

    private fun applyLottieDynamicColor() {
        val layerColors =
            mapOf(
                ".bg" to backgroundShapeColor,
                ".dot1" to backgroundDotColor1,
                ".dot2" to backgroundDotColor2,
                ".dot3" to backgroundDotColor3,
            )
        for ((layerName, color) in layerColors) {
            qrCodeViewForBackground.addValueCallback(
                KeyPath("**", layerName, "**"),
                LottieProperty.COLOR_FILTER,
            ) {
                PorterDuffColorFilter(color, PorterDuff.Mode.SRC)
            }
        }
    }

    private fun loadDynamicColors(context: Context) {
        foregroundColorPrimary = context.getColor(R.color.wifi_qr_primary_foreground)
        foregroundColorSecondary = context.getColor(R.color.wifi_qr_secondary_foreground)
        foregroundColorAccent = context.getColor(R.color.wifi_qr_accent_foreground)
        backgroundShapeColor = context.getColor(R.color.wifi_qr_background_surface)
        mainForegroundColorArray = arrayOf(foregroundColorPrimary, foregroundColorSecondary)

        backgroundDotColor1 =
            ColorUtils.setAlphaComponent(context.getColor(R.color.wifi_qr_primary_background), 0x26)
        backgroundDotColor2 =
            ColorUtils.setAlphaComponent(
                context.getColor(R.color.wifi_qr_secondary_background),
                0x19,
            )
        backgroundDotColor3 =
            ColorUtils.setAlphaComponent(
                context.getColor(R.color.wifi_qr_tertiary_background),
                0x4c,
            )
        mainBackgroundColorArray =
            arrayOf(backgroundDotColor1, backgroundDotColor2, backgroundDotColor3)
    }
}
