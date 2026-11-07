package com.kakario.mihome

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

class LoginResult(val ok: Boolean, val message: String, val verifyUrl: String? = null)
class AuthException : Exception("로그인이 만료됐어요. 다시 로그인해 주세요")
class Device(val did: String, val name: String, val model: String, val online: Boolean)

/** 샤오미 클라우드(중국 서버) 로그인 + 암호화 API 호출 */
object MiCloud {
    private const val API = "https://api.io.mi.com/app"

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("mi", Context.MODE_PRIVATE)
    fun loggedIn(c: Context) = prefs(c).getString("serviceToken", null) != null
    fun logout(c: Context) = prefs(c).edit().remove("serviceToken").remove("ssecurity").remove("userId").apply()

    private fun randStr(len: Int, chars: String): String {
        val r = SecureRandom()
        return (1..len).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }

    private fun stored(c: Context, key: String, make: () -> String): String {
        val p = prefs(c)
        p.getString(key, null)?.let { return it }
        val v = make(); p.edit().putString(key, v).apply(); return v
    }

    private fun ua(c: Context): String {
        val agent = stored(c, "agentId") { randStr(13, "ABCDEFGHIJKLMNOPQRSTUVWXYZ") }
        return "Android-7.1.1-1.0.0-ONEPLUS A3010-136-$agent APP/xiaomi.smarthome APPV/62830"
    }

    private fun deviceId(c: Context) = stored(c, "deviceId") { randStr(6, "abcdefghijklmnopqrstuvwxyz") }

    private class Resp(val code: Int, val body: String, val cookies: Map<String, String>, val location: String?)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun http(
        url: String, ua: String, method: String = "GET", form: Map<String, String>? = null,
        cookies: Map<String, String> = emptyMap(), headers: Map<String, String> = emptyMap()
    ): Resp {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.requestMethod = method
        conn.setRequestProperty("User-Agent", ua)
        if (cookies.isNotEmpty()) conn.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        headers.forEach { conn.setRequestProperty(it.key, it.value) }
        if (form != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = form.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code >= 400) conn.errorStream else conn.inputStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        val ck = mutableMapOf<String, String>()
        conn.headerFields.forEach { (k, v) ->
            if (k != null && k.equals("Set-Cookie", true)) v.forEach {
                val pair = it.substringBefore(";")
                val i = pair.indexOf('=')
                if (i > 0) ck[pair.substring(0, i).trim()] = pair.substring(i + 1).trim()
            }
        }
        val loc = conn.getHeaderField("Location")
        conn.disconnect()
        return Resp(code, body, ck, loc)
    }

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        .joinToString("") { "%02X".format(it) }

    private fun json(body: String) = JSONObject(body.replace("&&&START&&&", ""))

    fun login(c: Context, user: String, pass: String): LoginResult {
        val ua = ua(c)
        val base = mapOf("userId" to user, "sdkVersion" to "accountsdk-18.8.15", "deviceId" to deviceId(c))
        val r1 = http("https://account.xiaomi.com/pass/serviceLogin?sid=xiaomiio&_json=true", ua, cookies = base)
        val sign = json(r1.body).optString("_sign")
        val form = linkedMapOf(
            "sid" to "xiaomiio", "hash" to md5(pass), "callback" to "https://sts.api.io.mi.com/sts",
            "qs" to "%3Fsid%3Dxiaomiio%26_json%3Dtrue", "user" to user, "_sign" to sign, "_json" to "true"
        )
        val r2 = http("https://account.xiaomi.com/pass/serviceLoginAuth2", ua, "POST", form, base)
        val j2 = json(r2.body)
        val ssecurity = j2.optString("ssecurity")
        if (ssecurity.length < 4) {
            val n = j2.optString("notificationUrl")
            if (n.isNotEmpty()) return LoginResult(false, "본인 인증이 필요해요", if (n.startsWith("http")) n else "https://account.xiaomi.com$n")
            if (j2.optString("captchaUrl").isNotEmpty()) return LoginResult(false, "샤오미가 보안 문자를 요구해요. 잠시 뒤 다시 시도해 주세요")
            return LoginResult(false, "로그인 실패: 아이디·비밀번호를 확인해 주세요")
        }
        val userId = j2.optString("userId")
        val r3 = http(j2.optString("location"), ua, cookies = base)
        var st = r3.cookies["serviceToken"]
        if (st == null && r3.location != null) st = http(r3.location, ua).cookies["serviceToken"]
        if (st == null) return LoginResult(false, "서비스 토큰을 받지 못했어요")
        prefs(c).edit().putString("serviceToken", st).putString("ssecurity", ssecurity)
            .putString("userId", userId).putString("user", user).apply()
        return LoginResult(true, "로그인 완료")
    }

    // ---------- 암호화(RC4) API ----------
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.DEFAULT)

    private fun nonce(): String {
        val buf = ByteBuffer.allocate(12)
        val rnd = ByteArray(8).also { SecureRandom().nextBytes(it) }
        buf.put(rnd)
        buf.putInt((System.currentTimeMillis() / 60000).toInt())
        return b64(buf.array())
    }

    private fun signedNonce(ssecurity: String, nonce: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(unb64(ssecurity)); md.update(unb64(nonce))
        return b64(md.digest())
    }

    private fun encSig(path: String, sn: String, params: Map<String, String>): String {
        val parts = mutableListOf("POST", path)
        params.forEach { parts.add("${it.key}=${it.value}") }
        parts.add(sn)
        return b64(MessageDigest.getInstance("SHA-1").digest(parts.joinToString("&").toByteArray()))
    }

    private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xff)) and 0xff
            val t = s[i]; s[i] = s[j]; s[j] = t
        }
        var a = 0; var b = 0
        fun next(): Int {
            a = (a + 1) and 0xff; b = (b + s[a]) and 0xff
            val t = s[a]; s[a] = s[b]; s[b] = t
            return s[(s[a] + s[b]) and 0xff]
        }
        repeat(1024) { next() }
        return ByteArray(data.size) { k -> (data[k].toInt() xor next()).toByte() }
    }

    fun api(c: Context, path: String, data: JSONObject): JSONObject {
        val p = prefs(c)
        val ssecurity = p.getString("ssecurity", null) ?: throw AuthException()
        val st = p.getString("serviceToken", null) ?: throw AuthException()
        val uid = p.getString("userId", "") ?: ""
        val n = nonce()
        val sn = signedNonce(ssecurity, n)
        val key = unb64(sn)
        val params = linkedMapOf("data" to data.toString())
        params["rc4_hash__"] = encSig(path, sn, params)
        for (k in params.keys.toList()) params[k] = b64(rc4(key, params[k]!!.toByteArray()))
        params["signature"] = encSig(path, sn, params)
        params["ssecurity"] = ssecurity
        params["_nonce"] = n
        val cookies = mapOf(
            "userId" to uid, "yetAnotherServiceToken" to st, "serviceToken" to st, "locale" to "en_GB",
            "timezone" to "GMT+09:00", "is_daylight" to "0", "dst_offset" to "0", "channel" to "MI_APP_STORE"
        )
        val headers = mapOf(
            "Accept-Encoding" to "identity", "x-xiaomi-protocal-flag-cli" to "PROTOCAL-HTTP2",
            "MIOT-ENCRYPT-ALGORITHM" to "ENCRYPT-RC4"
        )
        val r = http(API + path, ua(c), "POST", params, cookies, headers)
        if (r.code == 401 || r.code == 403) { logout(c); throw AuthException() }
        if (r.code >= 400) throw Exception("서버 오류 ${r.code}")
        val txt = try { String(rc4(key, unb64(r.body))) } catch (e: Exception) { r.body }
        return JSONObject(txt)
    }

    fun devices(c: Context): List<Device> {
        val data = JSONObject().put("getVirtualModel", true).put("getHuamiDevices", 1)
            .put("get_split_device", false).put("support_smart_home", true)
        val r = api(c, "/home/device_list", data)
        val list = r.optJSONObject("result")?.optJSONArray("list") ?: throw Exception(r.optString("message", "기기 목록 없음"))
        prefs(c).edit().putString("devices", list.toString()).apply()
        return parse(list)
    }

    fun cachedDevices(c: Context): List<Device> =
        prefs(c).getString("devices", null)?.let { parse(JSONArray(it)) } ?: emptyList()

    private fun parse(list: JSONArray) = (0 until list.length()).map {
        val o = list.getJSONObject(it)
        Device(o.optString("did"), o.optString("name"), o.optString("model"), o.optBoolean("isOnline", true))
    }
}

/** Yeelight 조명 명령 (클라우드 경유 miio RPC) */
object Yee {
    fun rpc(c: Context, did: String, method: String, params: JSONArray): JSONObject {
        val r = MiCloud.api(c, "/home/rpc/$did", JSONObject().put("method", method).put("params", params))
        if (r.optInt("code", 0) != 0) throw Exception(r.optString("message", "명령 실패"))
        return r
    }

    fun status(c: Context, did: String): Map<String, String> {
        val keys = listOf("power", "bright", "ct", "active_mode", "nl_br")
        val res = rpc(c, did, "get_prop", JSONArray(keys)).optJSONArray("result") ?: throw Exception("상태 없음")
        return keys.indices.associate { keys[it] to (if (it < res.length()) res.optString(it) else "") }
    }

    fun power(c: Context, did: String, on: Boolean) =
        rpc(c, did, "set_power", JSONArray().put(if (on) "on" else "off").put("smooth").put(500))
    fun toggle(c: Context, did: String) = rpc(c, did, "toggle", JSONArray())
    fun mode(c: Context, did: String, night: Boolean) =
        rpc(c, did, "set_power", JSONArray().put("on").put("smooth").put(500).put(if (night) 5 else 1))
    fun bright(c: Context, did: String, v: Int) = rpc(c, did, "set_bright", JSONArray().put(v).put("smooth").put(500))
    fun ct(c: Context, did: String, v: Int) = rpc(c, did, "set_ct_abx", JSONArray().put(v).put("smooth").put(500))
    fun adjust(c: Context, did: String, up: Boolean) =
        rpc(c, did, "set_adjust", JSONArray().put(if (up) "increase" else "decrease").put("bright"))

    fun describe(s: Map<String, String>): String = when {
        s["power"] != "on" -> "꺼짐"
        s["active_mode"] == "1" -> "야간등 · 밝기 ${s["nl_br"]}%"
        else -> "켜짐 · 밝기 ${s["bright"]}% · ${s["ct"]}K"
    }
}
