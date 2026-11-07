package com.kakario.mihome

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** 홈 화면 조명 위젯: 전원 토글, 밝기 -/+, 야간등 */
class LightWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        ids.forEach { m.updateAppWidget(it, views(c, null)) }
    }

    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        val act = i.action ?: return
        if (!act.startsWith(PREFIX)) return
        val did = MiCloud.prefs(c).getString("widget_did", null) ?: return
        val pending = goAsync()
        pushAll(c, "보내는 중…")
        Thread {
            val msg = try {
                when (act) {
                    POWER -> Yee.toggle(c, did)
                    UP -> Yee.adjust(c, did, true)
                    DOWN -> Yee.adjust(c, did, false)
                    NIGHT -> Yee.mode(c, did, true)
                }
                Thread.sleep(400)
                Yee.describe(Yee.status(c, did))
            } catch (e: Exception) {
                "실패: ${e.message}"
            }
            pushAll(c, msg)
            pending.finish()
        }.start()
    }

    companion object {
        private const val PREFIX = "com.kakario.mihome.W_"
        private const val POWER = PREFIX + "POWER"
        private const val UP = PREFIX + "UP"
        private const val DOWN = PREFIX + "DOWN"
        private const val NIGHT = PREFIX + "NIGHT"

        fun pushAll(c: Context, status: String?) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, LightWidget::class.java)).forEach { m.updateAppWidget(it, views(c, status)) }
        }

        private fun pi(c: Context, action: String, code: Int) = PendingIntent.getBroadcast(
            c, code, Intent(c, LightWidget::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun views(c: Context, status: String?): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget)
            val name = MiCloud.prefs(c).getString("widget_name", null)
            rv.setTextViewText(R.id.w_name, name ?: "앱에서 기기를 골라 주세요")
            rv.setTextViewText(R.id.w_state, status ?: if (name != null) "버튼을 눌러 제어" else "")
            rv.setOnClickPendingIntent(R.id.w_power, pi(c, POWER, 1))
            rv.setOnClickPendingIntent(R.id.w_up, pi(c, UP, 2))
            rv.setOnClickPendingIntent(R.id.w_down, pi(c, DOWN, 3))
            rv.setOnClickPendingIntent(R.id.w_night, pi(c, NIGHT, 4))
            val open = PendingIntent.getActivity(
                c, 5, Intent(c, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.w_info, open)
            return rv
        }
    }
}
