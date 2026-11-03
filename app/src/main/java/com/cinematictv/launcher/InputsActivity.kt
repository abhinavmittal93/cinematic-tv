package com.cinematictv.launcher

import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log
import android.widget.Toast

/**
 * Source/Inputs sheet (tuner, HDMI, AV). Also answers com.android.tv.action.VIEW_INPUTS.
 */
class InputsActivity : PanelActivity() {

    override fun kicker(): CharSequence = getString(R.string.app_name)
    override fun sheetTitle(): CharSequence = getString(R.string.inputs)

    override fun build() {
        val tim = getSystemService(TV_INPUT_SERVICE) as? TvInputManager
        val inputs = try { tim?.tvInputList.orEmpty() } catch (t: Throwable) { emptyList() }
        val shown = inputs.filter { it.isPassthroughInput || it.type == TvInputInfo.TYPE_TUNER }
            .filter { !it.isHidden(this) }
            .sortedWith(compareBy<TvInputInfo>({ it.type != TvInputInfo.TYPE_TUNER }, { label(it) }))
            .distinctBy { if (it.type == TvInputInfo.TYPE_TUNER) "tuner" else it.id }

        for (info in shown) {
            val tuner = info.type == TvInputInfo.TYPE_TUNER
            val state = try { tim?.getInputState(info.id) } catch (_: Throwable) { null }
            val connected = state != TvInputManager.INPUT_STATE_DISCONNECTED
            addRow(
                icon = if (tuner) R.drawable.ic_tv else R.drawable.ic_input,
                title = if (tuner) getString(R.string.live_tv) else label(info),
                sub = if (tuner) null else getString(if (connected) R.string.connected else R.string.not_connected),
                dot = !tuner && connected && state == TvInputManager.INPUT_STATE_CONNECTED,
                dim = !connected,
                chevron = false,
            ) { open(info) }
        }
        if (shown.isEmpty()) addRow(R.drawable.ic_input, getString(R.string.no_inputs), chevron = false) { finish() }
    }

    private fun label(info: TvInputInfo): String =
        (info.loadCustomLabel(this) ?: info.loadLabel(this))?.toString() ?: info.id

    private fun open(info: TvInputInfo) {
        val uri = if (info.isPassthroughInput) TvContract.buildChannelUriForPassthroughInput(info.id)
        else TvContract.buildChannelsUriForInput(info.id)
        val type = if (info.isPassthroughInput) TvContract.Channels.CONTENT_ITEM_TYPE else TvContract.Channels.CONTENT_TYPE
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        } catch (t: Throwable) {
            Log.w("CinematicTV", "Cannot open input ${info.id}", t)
            Toast.makeText(this, getString(R.string.app_unavailable, label(info)), Toast.LENGTH_SHORT).show()
        }
    }
}
