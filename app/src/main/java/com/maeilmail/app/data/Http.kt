package com.maeilmail.app.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal object Http {
    private const val USER_AGENT = "MaeilMailApp/0.1 (Android)"

    class Response(val code: Int, val bytes: ByteArray) {
        val text: String get() = bytes.toString(Charsets.UTF_8)
        val ok: Boolean get() = code in 200..299
    }

    /** HTTP 오류 응답. 응답 코드로 원인을 가른다 (403·429: 요청 한도, 404: 없음) */
    class StatusException(val code: Int) : IOException("HTTP $code")

    fun get(url: String, headers: Map<String, String> = emptyMap(), maxBytes: Int = 5_000_000): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.use { it.readLimited(maxBytes) } ?: ByteArray(0)
            return Response(code, bytes)
        } finally {
            conn.disconnect()
        }
    }

    /** 성공 응답의 본문. 실패하면 StatusException */
    fun getOk(url: String, headers: Map<String, String> = emptyMap()): Response =
        get(url, headers).also { if (!it.ok) throw StatusException(it.code) }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > limit) throw IOException("응답이 너무 큽니다")
        }
        return out.toByteArray()
    }
}
