package com.dzung.runner.locationtracker.util

import android.graphics.*
import com.dzung.runner.locationtracker.data.database.LocationPoint
import com.dzung.runner.locationtracker.data.database.RunSession
import kotlin.math.cos
import kotlin.math.min

/**
 * Aspect ratio configurations for social media sharing.
 */
enum class RouteAspectRatio(val width: Int, val height: Int, val label: String) {
    SQUARE_1_1(1080, 1080, "1:1 Square"),
    STORY_9_16(1080, 1920, "9:16 Story")
}

/**
 * Theme color palettes inspired by Strava and athletic trackers.
 */
enum class RouteTheme(
    val primaryColor: Int,
    val textColor: Int,
    val labelColor: Int,
    val shadowColor: Int,
    val isDarkTone: Boolean
) {
    STRAVA_ORANGE(
        primaryColor = 0xFFFC5200.toInt(), // Iconic Strava Orange
        textColor = 0xFFFFFFFF.toInt(),
        labelColor = 0xCCFFFFFF.toInt(),
        shadowColor = 0x99000000.toInt(),
        isDarkTone = false
    ),
    CLEAN_WHITE(
        primaryColor = 0xFFFFFFFF.toInt(),
        textColor = 0xFFFFFFFF.toInt(),
        labelColor = 0xCCFFFFFF.toInt(),
        shadowColor = 0xB3000000.toInt(), // Strong shadow for contrast on photo backdrops
        isDarkTone = false
    ),
    ELECTRIC_NEON(
        primaryColor = 0xFF00E676.toInt(), // Athletic Neon Green
        textColor = 0xFFFFFFFF.toInt(),
        labelColor = 0xCCFFFFFF.toInt(),
        shadowColor = 0x99000000.toInt(),
        isDarkTone = false
    ),
    STEALTH_DARK(
        primaryColor = 0xFF1C1C1E.toInt(), // Stealth Black
        textColor = 0xFF1C1C1E.toInt(),
        labelColor = 0x991C1C1E.toInt(),
        shadowColor = 0x33FFFFFF.toInt(),
        isDarkTone = true
    )
}

/**
 * Visual mode: full stats overlay or minimalist route only.
 */
enum class RouteOverlayStyle {
    ROUTE_AND_STATS,
    ROUTE_ONLY
}

/**
 * Configuration options for rendering the route sticker.
 */
data class RouteStickerConfig(
    val aspectRatio: RouteAspectRatio = RouteAspectRatio.SQUARE_1_1,
    val theme: RouteTheme = RouteTheme.STRAVA_ORANGE,
    val style: RouteOverlayStyle = RouteOverlayStyle.ROUTE_AND_STATS,
    val showBrandWatermark: Boolean = true
)

/**
 * High-performance 2D Canvas renderer that creates transparent PNG stickers
 * of running routes and workout statistics (similar to Strava / Nike Run Club).
 */
object RouteBitmapGenerator {

    /**
     * Generates a transparent PNG Bitmap (ARGB_8888) showing the route polyline and stats.
     */
    fun generateTransparentRouteBitmap(
        points: List<LocationPoint>,
        session: RunSession,
        config: RouteStickerConfig
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(config.aspectRatio.width, config.aspectRatio.height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)

        renderOverlay(canvas, points, session, config, width = config.aspectRatio.width, height = config.aspectRatio.height)
        return bitmap
    }

    /**
     * Composites an optional user photo backdrop with the transparent route & stats overlay.
     */
    fun generateCompositedBitmap(
        backgroundPhoto: Bitmap,
        points: List<LocationPoint>,
        session: RunSession,
        config: RouteStickerConfig
    ): Bitmap {
        val targetWidth = config.aspectRatio.width
        val targetHeight = config.aspectRatio.height

        val composited = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composited)

        // 1. Center-crop scale and draw background photo
        val scale = maxOf(
            targetWidth.toFloat() / backgroundPhoto.width,
            targetHeight.toFloat() / backgroundPhoto.height
        )
        val scaledW = backgroundPhoto.width * scale
        val scaledH = backgroundPhoto.height * scale
        val dx = (targetWidth - scaledW) / 2f
        val dy = (targetHeight - scaledH) / 2f

        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val destRect = RectF(dx, dy, dx + scaledW, dy + scaledH)
        canvas.drawBitmap(backgroundPhoto, null, destRect, photoPaint)

        // 2. Add subtle dark gradient overlay at top and bottom for text legibility if white/colored text
        if (!config.theme.isDarkTone) {
            val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, targetHeight * 0.55f,
                    0f, targetHeight.toFloat(),
                    intArrayOf(Color.TRANSPARENT, 0x99000000.toInt()),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, targetHeight * 0.55f, targetWidth.toFloat(), targetHeight.toFloat(), gradientPaint)
        }

        // 3. Render route and stats on top
        renderOverlay(canvas, points, session, config, targetWidth, targetHeight)

        return composited
    }

    private fun renderOverlay(
        canvas: Canvas,
        points: List<LocationPoint>,
        session: RunSession,
        config: RouteStickerConfig,
        width: Int,
        height: Int
    ) {
        val isSquare = config.aspectRatio == RouteAspectRatio.SQUARE_1_1

        // Define layout zones
        val topPadding = if (isSquare) 80f else 140f
        val bottomPadding = if (isSquare) 70f else 120f
        val horizontalPadding = 80f

        // Stats section height
        val statsHeight = if (config.style == RouteOverlayStyle.ROUTE_AND_STATS) {
            if (isSquare) 280f else 340f
        } else {
            0f
        }

        // Route bounding box
        val routeTop = topPadding + if (config.showBrandWatermark) 60f else 0f
        val routeBottom = height - bottomPadding - statsHeight - 30f
        val routeRect = RectF(horizontalPadding, routeTop, width - horizontalPadding, routeBottom)

        // 1. Draw Watermark Header
        if (config.showBrandWatermark) {
            drawBrandWatermark(canvas, topPadding, width, config.theme)
        }

        // 2. Draw GPS Route
        if (points.size >= 2 && routeRect.height() > 100f && routeRect.width() > 100f) {
            drawRoutePath(canvas, points, routeRect, config.theme)
        }

        // 3. Draw Stats Block
        if (config.style == RouteOverlayStyle.ROUTE_AND_STATS) {
            val statsStartY = height - bottomPadding - statsHeight
            drawWorkoutStats(canvas, session, statsStartY, width, config.theme, isSquare)
        }
    }

    private fun drawBrandWatermark(
        canvas: Canvas,
        topY: Float,
        width: Int,
        theme: RouteTheme
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 28f
            letterSpacing = 0.15f
            setShadowLayer(6f, 0f, 3f, theme.shadowColor)
            textAlign = Paint.Align.LEFT
        }

        // Accent indicator dot
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.primaryColor
            style = Paint.Style.FILL
            setShadowLayer(6f, 0f, 3f, theme.shadowColor)
        }

        val marginX = 80f
        val dotRadius = 8f
        val dotY = topY + 18f
        canvas.drawCircle(marginX + dotRadius, dotY, dotRadius, dotPaint)

        canvas.drawText("LOCATION TRACKER", marginX + dotRadius * 2 + 16f, topY + 28f, paint)
    }

    private fun drawRoutePath(
        canvas: Canvas,
        points: List<LocationPoint>,
        bounds: RectF,
        theme: RouteTheme
    ) {
        var minLat = points.minOf { it.latitude }
        var maxLat = points.maxOf { it.latitude }
        var minLng = points.minOf { it.longitude }
        var maxLng = points.maxOf { it.longitude }

        // Expand bounds slightly if flat/stationary to avoid zero division
        if (maxLat - minLat < 0.0001) {
            maxLat += 0.0001
            minLat -= 0.0001
        }
        if (maxLng - minLng < 0.0001) {
            maxLng += 0.0001
            minLng -= 0.0001
        }

        // Equirectangular projection with reference latitude cosine correction
        val meanLatRad = Math.toRadians((minLat + maxLat) / 2.0)
        val cosLat = cos(meanLatRad)

        val projWidth = (maxLng - minLng) * cosLat
        val projHeight = maxLat - minLat

        val scale = min(bounds.width() / projWidth, bounds.height() / projHeight)
        val scaledW = (projWidth * scale).toFloat()
        val scaledH = (projHeight * scale).toFloat()

        val startX = bounds.left + (bounds.width() - scaledW) / 2f
        val startY = bounds.top + (bounds.height() - scaledH) / 2f

        fun mapPoint(point: LocationPoint): PointF {
            val px = startX + ((point.longitude - minLng) * cosLat * scale).toFloat()
            val py = startY + ((maxLat - point.latitude) * scale).toFloat()
            return PointF(px, py)
        }

        val screenPoints = points.map { mapPoint(it) }

        val path = Path()
        path.moveTo(screenPoints.first().x, screenPoints.first().y)
        for (i in 1 until screenPoints.size) {
            path.lineTo(screenPoints[i].x, screenPoints[i].y)
        }

        // Route Polyline Paint
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.primaryColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 14f
            setShadowLayer(10f, 0f, 4f, theme.shadowColor)
        }
        canvas.drawPath(path, routePaint)

        // Draw Start Dot (Green with white outline)
        val startPt = screenPoints.first()
        val finishPt = screenPoints.last()

        val whiteRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(6f, 0f, 3f, 0x88000000.toInt())
        }

        val startDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00E676.toInt() // Green
            style = Paint.Style.FILL
        }
        canvas.drawCircle(startPt.x, startPt.y, 16f, whiteRingPaint)
        canvas.drawCircle(startPt.x, startPt.y, 11f, startDotPaint)

        // Draw Finish Dot (Red with white outline)
        val finishDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF44336.toInt() // Red
            style = Paint.Style.FILL
        }
        canvas.drawCircle(finishPt.x, finishPt.y, 16f, whiteRingPaint)
        canvas.drawCircle(finishPt.x, finishPt.y, 11f, finishDotPaint)
    }

    private fun drawWorkoutStats(
        canvas: Canvas,
        session: RunSession,
        startY: Float,
        width: Int,
        theme: RouteTheme,
        isSquare: Boolean
    ) {
        val elapsedSec = if (session.endTimeInMillis != null) {
            (session.endTimeInMillis - session.startTimeInMillis) / 1000
        } else {
            0L
        }

        val distKm = session.totalDistanceMeters / 1000f
        val distStr = String.format(java.util.Locale.US, "%.2f", distKm)

        val h = elapsedSec / 3600
        val m = (elapsedSec % 3600) / 60
        val s = elapsedSec % 60
        val timeStr = if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)

        val avgPaceStr = if (session.totalDistanceMeters > 0) {
            val paceSec = (elapsedSec / (session.totalDistanceMeters / 1000f)).toInt()
            val pMin = paceSec / 60
            val pSec = paceSec % 60
            String.format("%d:%02d", pMin, pSec)
        } else {
            "-:--"
        }
        val calStr = "${session.totalCalories}"

        val leftMargin = 80f

        // 1. Prominent Distance Display (Strava style)
        val distanceNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isSquare) 84f else 96f
            setShadowLayer(8f, 0f, 4f, theme.shadowColor)
            textAlign = Paint.Align.LEFT
        }

        val distanceUnitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.primaryColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isSquare) 36f else 42f
            setShadowLayer(6f, 0f, 3f, theme.shadowColor)
            textAlign = Paint.Align.LEFT
        }

        val distanceNumWidth = distanceNumPaint.measureText(distStr)
        val distBaselineY = startY + if (isSquare) 80f else 95f

        canvas.drawText(distStr, leftMargin, distBaselineY, distanceNumPaint)
        canvas.drawText(" km", leftMargin + distanceNumWidth, distBaselineY, distanceUnitPaint)

        // Label DISTANCE
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.labelColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 24f
            letterSpacing = 0.12f
            setShadowLayer(4f, 0f, 2f, theme.shadowColor)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("DISTANCE", leftMargin, distBaselineY + 34f, labelPaint)

        // 2. Metrics Grid: Time, Pace, Calories
        val subMetricsY = distBaselineY + if (isSquare) 110f else 135f
        val colWidth = (width - leftMargin * 2) / 3f

        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isSquare) 44f else 50f
            setShadowLayer(6f, 0f, 3f, theme.shadowColor)
            textAlign = Paint.Align.LEFT
        }

        // Metric 1: Time
        canvas.drawText(timeStr, leftMargin, subMetricsY, valPaint)
        canvas.drawText("TIME", leftMargin, subMetricsY + 32f, labelPaint)

        // Metric 2: Pace
        val col2X = leftMargin + colWidth
        canvas.drawText("$avgPaceStr /km", col2X, subMetricsY, valPaint)
        canvas.drawText("AVG PACE", col2X, subMetricsY + 32f, labelPaint)

        // Metric 3: Calories
        val col3X = leftMargin + colWidth * 2
        canvas.drawText("$calStr kcal", col3X, subMetricsY, valPaint)
        canvas.drawText("CALORIES", col3X, subMetricsY + 32f, labelPaint)
    }
}
