package com.ssafy.woojuin.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.ssafy.woojuin.R
import com.ssafy.woojuin.presentation.MainActivity

private const val RESOURCES_VERSION = "1"
private const val ID_LOGO = "logo"

/**
 * 우주인 Tile — 앱 홈의 복제가 아니라 음성 저장 직행 진입점.
 * Title: 우주인 / Main: 로고 + 말해서 저장 / Bottom: 자료 찾기·노래 찾기
 */
class WoojuinTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = CallbackToFutureAdapter.getFuture { completer ->
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(tileLayout(requestParams))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
        completer.set(tile)
        "tileRequest"
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = CallbackToFutureAdapter.getFuture { completer ->
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .addIdToImageMapping(
                ID_LOGO,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.ic_woojuin_logo)
                            .build()
                    )
                    .build()
            )
            .build()
        completer.set(resources)
        "tileResources"
    }

    private fun launchAction(route: String): ActionBuilders.LaunchAction =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(
                        MainActivity.EXTRA_ROUTE,
                        ActionBuilders.AndroidStringExtra.Builder().setValue(route).build(),
                    )
                    .build()
            )
            .build()

    private fun clickable(id: String, route: String): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId(id)
            .setOnClick(launchAction(route))
            .build()

    private fun tileLayout(
        requestParams: RequestBuilders.TileRequest,
    ): LayoutElementBuilders.LayoutElement {
        val deviceParameters = requestParams.deviceConfiguration

        return LayoutElementBuilders.Column.Builder()
            .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
            .addContent(
                Text.Builder(this, getString(R.string.app_name))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(0xFF9AA0AC.toInt()))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build()
            )
            .addContent(
                // 중앙 정적 로고 — 탭하면 음성 저장으로 직접 진입
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(ID_LOGO)
                    .setWidth(dp(64f))
                    .setHeight(dp(64f))
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setClickable(clickable("voice", MainActivity.ROUTE_VOICE))
                            .setSemantics(
                                ModifiersBuilders.Semantics.Builder()
                                    .setContentDescription(getString(R.string.complication_voice_save))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).build()
            )
            .addContent(
                Text.Builder(this, getString(R.string.tile_voice_save))
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(0xFFE8EAEE.toInt()))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build()
            )
            .addContent(
                LayoutElementBuilders.Row.Builder()
                    .addContent(
                        CompactChip.Builder(
                            this,
                            getString(R.string.tile_search),
                            clickable("search", MainActivity.ROUTE_SEARCH),
                            deviceParameters,
                        ).build()
                    )
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).build()
                    )
                    .addContent(
                        CompactChip.Builder(
                            this,
                            getString(R.string.tile_song),
                            clickable("song", MainActivity.ROUTE_SONG),
                            deviceParameters,
                        ).build()
                    )
                    .build()
            )
            .build()
    }
}