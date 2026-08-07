package com.ssafy.woojuin.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.ssafy.woojuin.R
import com.ssafy.woojuin.presentation.MainActivity

/**
 * 우주인 Complication — 정적 로고 심벌만 표시하고,
 * 탭하면 앱 홈이 아니라 음성 저장으로 직접 진입한다.
 */
class WoojuinComplicationService : SuspendingComplicationDataSourceService() {

    private fun tapAction(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_VOICE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun logoIcon(): Icon = Icon.createWithResource(this, R.drawable.ic_woojuin_logo)

    private fun buildData(type: ComplicationType): ComplicationData? {
        val description = PlainComplicationText.Builder(getString(R.string.complication_voice_save)).build()
        return when (type) {
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(
                    MonochromaticImage.Builder(logoIcon()).build(),
                    description,
                )
                    .setTapAction(tapAction())
                    .build()

            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(logoIcon(), SmallImageType.ICON).build(),
                    description,
                )
                    .setTapAction(tapAction())
                    .build()

            else -> null
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = buildData(type)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        buildData(request.complicationType)
}