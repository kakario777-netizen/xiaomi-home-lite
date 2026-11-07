package com.kakario.mihome

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val bg = Color.parseColor("#101214")
    private val card = Color.parseColor("#1C1F22")
    private val accent = Color.parseColor("#2BB3A3")
    private val sub = Color.parseColor("#9AA0A6")
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)
        if (MiCloud.loggedIn(this)) showDevices() else showLogin(null)
    }

    // ---------- 작은 도우미 ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun round(color: Int, r: Int = 16) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private fun text(t: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun btn(t: String, color: Int = Color.parseColor("#2A2E33"), on: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(Color.WHITE); textSize = 14f
        background = round(color, 12); minHeight = dp(44)
        setOnClickListener { on() }
    }
    private fun wrapLp(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }
    private fun weightLp() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
    private fun work(job: () -> Unit) = Thread(job).start()
    private fun toast(m: String) = runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_SHORT).show() }

    // ---------- 로그인 ----------
    private fun showLogin(msg: String?) {
        root.removeAllViews()
        root.addView(text("샤오미 홈 라이트", 24f, bold = true))
        root.addView(text("샤오미 계정으로 로그인 (중국 서버)", 13f, sub), wrapLp(4))
        val id = EditText(this).apply {
            hint = "샤오미 계정 (전화번호·이메일·ID)"; setTextColor(Color.WHITE); setHintTextColor(sub)
            setText(MiCloud.prefs(this@MainActivity).getString("user", ""))
            background = round(card, 12); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val pw = EditText(this).apply {
            hint = "비밀번호"; setTextColor(Color.WHITE); setHintTextColor(sub)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = round(card, 12); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val status = text(msg ?: "", 13f, sub)
        val capImg = ImageView(this).apply { visibility = View.GONE; setBackgroundColor(Color.WHITE); adjustViewBounds = true }
        val capIn = EditText(this).apply {
            hint = "보안 문자 입력"; setTextColor(Color.WHITE); setHintTextColor(sub); visibility = View.GONE
            background = round(card, 12); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(id, wrapLp(24)); root.addView(pw, wrapLp(10))
        root.addView(capImg, LinearLayout.LayoutParams(dp(200), dp(70)).apply { topMargin = dp(10) })
        root.addView(capIn, wrapLp(8))
        lateinit var go: () -> Unit
        go = {
            status.text = "로그인 중…"
            val u = id.text.toString().trim(); val p = pw.text.toString()
            val code = if (capIn.visibility == View.VISIBLE) capIn.text.toString().trim().ifEmpty { null } else null
            work {
                val r = try { MiCloud.login(this, u, p, code) } catch (e: Exception) { LoginResult(false, "연결 실패: ${e.message}") }
                runOnUiThread {
                    when {
                        r.ok -> showDevices()
                        r.captcha != null -> {
                            status.text = r.message
                            capImg.setImageBitmap(BitmapFactory.decodeByteArray(r.captcha, 0, r.captcha.size))
                            capImg.visibility = View.VISIBLE
                            capIn.setText(""); capIn.visibility = View.VISIBLE; capIn.requestFocus()
                        }
                        r.verifyUrl != null -> { status.text = r.message; verify(r.verifyUrl) { go() } }
                        else -> status.text = r.message
                    }
                }
            }
        }
        root.addView(btn("로그인", accent) { go() }, wrapLp(16))
        root.addView(status, wrapLp(12))
        root.addView(text("비밀번호는 저장하지 않고, 로그인 후 받은 토큰만 이 폰에 보관해요.", 12f, sub), wrapLp(20))
    }

    private fun verify(url: String, retry: () -> Unit) {
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl(url)
        }
        val box = LinearLayout(this).apply { addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(520))) }
        AlertDialog.Builder(this)
            .setTitle("본인 인증 후 '인증 완료'를 눌러 주세요")
            .setView(box)
            .setPositiveButton("인증 완료") { _, _ -> retry() }
            .setNegativeButton("취소", null)
            .show()
    }

    // ---------- 기기 목록 ----------
    private fun showDevices() {
        root.removeAllViews()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("우리집 기기", 24f, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(btn("새로고침") { showDevices() })
        head.addView(btn("로그아웃") { MiCloud.logout(this); showLogin(null) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6) })
        root.addView(head)
        val status = text("기기를 불러오는 중…", 13f, sub)
        root.addView(status, wrapLp(8))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        val cached = MiCloud.cachedDevices(this)
        cached.forEach { list.addView(deviceCard(it), wrapLp(12)) }
        work {
            try {
                val devs = MiCloud.devices(this)
                runOnUiThread {
                    status.text = "기기 ${devs.size}개"
                    list.removeAllViews()
                    devs.sortedByDescending { it.model.startsWith("yeelink.light") }
                        .forEach { list.addView(deviceCard(it), wrapLp(12)) }
                }
            } catch (e: AuthException) {
                runOnUiThread { showLogin(e.message) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "불러오기 실패: ${e.message}" }
            }
        }
    }

    private fun deviceCard(d: Device): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = round(card)
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        c.addView(text(d.name, 17f, bold = true))
        c.addView(text(d.model + if (!d.online) " · 오프라인" else "", 12f, sub))
        if (!d.model.startsWith("yeelink.light")) {
            c.addView(text("이 기기는 다음 업데이트에서 지원할게요", 13f, sub), wrapLp(8))
            return c
        }
        val state = text("상태 확인 중…", 13f, accent)
        c.addView(state, wrapLp(6))

        val brLabel = text("밝기", 13f, sub)
        val br = SeekBar(this).apply { min = 1; max = 100 }
        val ctLabel = text("색온도", 13f, sub)
        val ct = SeekBar(this).apply { min = 2700; max = 6500 }

        fun refresh() = work {
            try {
                val s = Yee.status(this, d.did)
                runOnUiThread {
                    state.text = Yee.describe(s)
                    s["bright"]?.toIntOrNull()?.let { br.progress = it; brLabel.text = "밝기 $it%" }
                    s["ct"]?.toIntOrNull()?.let { ct.progress = it; ctLabel.text = "색온도 ${it}K" }
                }
            } catch (e: AuthException) { runOnUiThread { showLogin(e.message) }
            } catch (e: Exception) { runOnUiThread { state.text = "상태 실패: ${e.message}" } }
        }
        fun send(job: () -> Unit) = work {
            try { job(); Thread.sleep(400) } catch (e: Exception) { toast("실패: ${e.message}") }
            refresh()
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(btn("켜기", accent) { send { Yee.power(this, d.did, true) } }, weightLp())
        row1.addView(btn("끄기") { send { Yee.power(this, d.did, false) } }, weightLp())
        row1.addView(btn("주간") { send { Yee.mode(this, d.did, false) } }, weightLp())
        row1.addView(btn("야간등") { send { Yee.mode(this, d.did, true) } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row1, wrapLp(12))

        br.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, user: Boolean) { if (user) brLabel.text = "밝기 $v%" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { val v = br.progress; send { Yee.bright(this@MainActivity, d.did, v) } }
        })
        ct.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, user: Boolean) { if (user) ctLabel.text = "색온도 ${v}K" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { val v = ct.progress; send { Yee.ct(this@MainActivity, d.did, v) } }
        })
        c.addView(brLabel, wrapLp(14)); c.addView(br, wrapLp(2))
        c.addView(ctLabel, wrapLp(10)); c.addView(ct, wrapLp(2))

        val isWidget = MiCloud.prefs(this).getString("widget_did", null) == d.did
        c.addView(btn(if (isWidget) "위젯에 표시 중" else "이 기기를 위젯에 표시") {
            MiCloud.prefs(this).edit().putString("widget_did", d.did).putString("widget_name", d.name).apply()
            LightWidget.pushAll(this, null)
            toast("위젯 기기로 지정했어요. 홈 화면에 '조명 위젯'을 추가하세요")
            showDevices()
        }, wrapLp(14))
        refresh()
        return c
    }
}
