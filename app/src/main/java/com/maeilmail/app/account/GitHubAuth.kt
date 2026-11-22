package com.maeilmail.app.account

import com.maeilmail.app.data.Http
import org.json.JSONObject
import java.io.IOException

/**
 * GitHub Device Flow 로그인 (GithubWidget과 같은 방식).
 * 1) requestDeviceCode()로 받은 코드를 사용자가 github.com/login/device에 적어 승인하고
 * 2) 그동안 앱은 interval초마다 poll()로 토큰을 받는다.
 * Device Flow는 Client Secret 없이 동작하므로 앱에 비밀값을 넣지 않는다.
 *
 * 앱은 웹과 같은 GitHub App(maeil-mail-reborn-memo)으로 로그인한다. 웹은 Firebase가 이 앱으로 GitHub 로그인을 하므로,
 * 같은 앱의 토큰이면 웹과 같은 권한(메모 저장소 목록, 커밋)을 쓰고 Firebase에도 같은 계정으로 들어간다.
 * 이 앱의 사용자 토큰은 8시간 뒤 만료되고, 갱신 토큰(6개월)으로 새 묶음을 받는다.
 */
object GitHubAuth {
    /** GitHub App maeil-mail-reborn-memo의 Client ID (비밀값이 아니다) */
    const val CLIENT_ID = "Iv23libszUzRdipVaOUV"

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        /** 확인 간격(초) */
        val interval: Int,
        /** 코드 만료 시각 (epoch ms) */
        val expiresAt: Long,
    )

    /** 로그인으로 받은 토큰 묶음. 만료가 없는 토큰이면 refreshToken·만료 시각이 null */
    data class TokenSet(
        val accessToken: String,
        val refreshToken: String?,
        /** access token 만료 시각 (epoch ms) */
        val accessExpiresAt: Long?,
        /** refresh token 만료 시각 (epoch ms) */
        val refreshExpiresAt: Long?,
    )

    sealed interface Poll {
        data class Success(val tokens: TokenSet) : Poll
        /** 아직 승인하지 않음 */
        data object Pending : Poll
        /** 너무 자주 물었음: 간격을 늘린다 */
        data class SlowDown(val interval: Int) : Poll
        data class Failed(val error: String, val description: String?) : Poll
    }

    sealed interface Refresh {
        data class Refreshed(val tokens: TokenSet) : Refresh
        /** 갱신 토큰도 만료·취소됨: 다시 로그인해야 한다 */
        data class Invalid(val error: String, val description: String?) : Refresh
    }

    /** 로그인 서버가 돌려준 오류 */
    class AuthException(val error: String, val description: String?) : IOException(message(error, description))

    /** GitHub 오류 코드 → 화면에 보일 문구 */
    fun message(error: String, description: String?): String = when (error) {
        "expired_token" -> "코드가 만료됐어요. 다시 로그인해 주세요"
        "access_denied" -> "GitHub에서 승인을 취소했어요"
        "bad_refresh_token" -> "로그인이 만료됐어요. 다시 로그인해 주세요"
        "device_flow_disabled" -> "GitHub App 설정에서 Device Flow가 꺼져 있어요"
        "incorrect_client_credentials" -> "GitHub App의 Client ID가 맞지 않아요"
        "unsupported_grant_type" -> "GitHub가 이 갱신 방식을 받지 않았어요"
        else -> description ?: error.ifEmpty { "GitHub 로그인에 실패했어요" }
    }

    fun requestDeviceCode(): DeviceCode {
        if (CLIENT_ID.isEmpty()) throw IOException("GitHub App의 Client ID를 아직 넣지 않았어요")
        val o = post("https://github.com/login/device/code", "client_id" to CLIENT_ID)
        if (o.has("error")) throw AuthException(o.optString("error"), o.optString("error_description").ifEmpty { null })
        return DeviceCode(
            deviceCode = o.getString("device_code"),
            userCode = o.getString("user_code"),
            verificationUri = o.getString("verification_uri"),
            interval = o.optInt("interval", 5),
            expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 900) * 1000,
        )
    }

    fun poll(deviceCode: String, currentInterval: Int): Poll = parsePoll(
        post(
            "https://github.com/login/oauth/access_token",
            "client_id" to CLIENT_ID,
            "device_code" to deviceCode,
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
        ),
        currentInterval,
    )

    /**
     * 갱신 토큰으로 새 토큰 묶음을 받는다. 한 번 쓴 갱신 토큰과 이전 access token은 더 쓸 수 없으니 새 묶음을 꼭 저장한다.
     * 네트워크 오류는 IOException으로 던진다.
     */
    fun refresh(refreshToken: String): Refresh = parseRefresh(
        post(
            "https://github.com/login/oauth/access_token",
            "client_id" to CLIENT_ID,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        ),
    )

    internal fun parsePoll(o: JSONObject, currentInterval: Int): Poll {
        parseTokens(o)?.let { return Poll.Success(it) }
        return when (val error = o.optString("error")) {
            "authorization_pending" -> Poll.Pending
            "slow_down" -> Poll.SlowDown(o.optInt("interval", currentInterval + 5))
            else -> Poll.Failed(error, o.optString("error_description").ifEmpty { null })
        }
    }

    internal fun parseRefresh(o: JSONObject): Refresh =
        parseTokens(o)?.let { Refresh.Refreshed(it) }
            ?: Refresh.Invalid(o.optString("error"), o.optString("error_description").ifEmpty { null })

    /** 토큰 응답에서 토큰 묶음을 꺼낸다. access_token이 없으면 null */
    internal fun parseTokens(o: JSONObject, now: Long = System.currentTimeMillis()): TokenSet? {
        val access = o.optString("access_token").takeIf { it.isNotEmpty() } ?: return null
        fun expiry(key: String) = o.optLong(key, 0L).takeIf { it > 0 }?.let { now + it * 1000 }
        return TokenSet(
            accessToken = access,
            refreshToken = o.optString("refresh_token").takeIf { it.isNotEmpty() },
            accessExpiresAt = expiry("expires_in"),
            refreshExpiresAt = expiry("refresh_token_expires_in"),
        )
    }

    private fun post(url: String, vararg params: Pair<String, String>): JSONObject {
        val res = Http.postForm(url, params.toList(), mapOf("Accept" to "application/json"))
        val text = res.text
        if (!text.trimStart().startsWith("{")) throw Http.StatusException(res.code, text)
        return JSONObject(text)
    }
}
