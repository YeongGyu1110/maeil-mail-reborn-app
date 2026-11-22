package com.maeilmail.app.account

import com.maeilmail.app.data.Http
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * 웹과 같은 Firebase 프로젝트를 REST로 쓴다 (Firebase SDK·안드로이드 앱 등록 없이).
 * 값은 웹 firebase-config.js와 같다. 웹 설정값은 공개돼도 되는 값이고, 데이터는 firestore.rules가 지킨다.
 */
object FirebaseConfig {
    const val API_KEY = "AIzaSyDuaO7uSY4vRbdYP4AJPtk3YoYlGyR5PZM"
    const val PROJECT_ID = "maeil-mail-reborn"
}

/** Firebase 로그인 결과. idToken으로 Firestore를 읽고 쓰며, 1시간마다 refreshToken으로 새로 받는다 */
data class FirebaseSession(
    val uid: String,
    val idToken: String,
    val refreshToken: String,
    /** idToken 만료 시각 (epoch ms) */
    val expiresAt: Long,
)

/** Firebase Auth REST (Identity Toolkit, Secure Token) */
object FirebaseAuthRest {
    /** Firebase 오류. message는 Firebase 오류 코드 (예: INVALID_IDP_RESPONSE) */
    class FirebaseException(val code: Int, message: String) : IOException(message)

    data class SignIn(val session: FirebaseSession, val githubLogin: String?, val githubId: String?)

    /** GitHub 토큰으로 로그인한다. 웹에서 GitHub로 로그인한 계정과 같은 uid가 된다 */
    fun signInWithGitHub(githubToken: String, now: Long = System.currentTimeMillis()): SignIn {
        val body = JSONObject()
            .put("postBody", "access_token=$githubToken&providerId=github.com")
            .put("requestUri", "http://localhost")
            .put("returnIdpCredential", true)
            .put("returnSecureToken", true)
        val o = call(
            Http.sendJson(
                "POST",
                "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=${FirebaseConfig.API_KEY}",
                body.toString(),
            ),
        )
        val raw = o.optString("rawUserInfo").takeIf { it.startsWith("{") }?.let(::JSONObject)
        return SignIn(
            session = FirebaseSession(
                uid = o.getString("localId"),
                idToken = o.getString("idToken"),
                refreshToken = o.getString("refreshToken"),
                expiresAt = now + o.optString("expiresIn", "3600").toLong() * 1000,
            ),
            githubLogin = o.optString("screenName").ifEmpty { raw?.optString("login").orEmpty() }.ifEmpty { null },
            githubId = raw?.opt("id")?.toString() ?: o.optString("federatedId").substringAfterLast('/').ifEmpty { null },
        )
    }

    /** idToken을 새로 받는다 (Firebase의 refreshToken은 로그아웃하거나 계정이 막히기 전까지 쓸 수 있다) */
    fun refresh(session: FirebaseSession, now: Long = System.currentTimeMillis()): FirebaseSession {
        val o = call(
            Http.postForm(
                "https://securetoken.googleapis.com/v1/token?key=${FirebaseConfig.API_KEY}",
                listOf("grant_type" to "refresh_token", "refresh_token" to session.refreshToken),
            ),
        )
        return FirebaseSession(
            uid = o.optString("user_id", session.uid),
            idToken = o.getString("id_token"),
            refreshToken = o.optString("refresh_token", session.refreshToken),
            expiresAt = now + o.optString("expires_in", "3600").toLong() * 1000,
        )
    }

    private fun call(res: Http.Response): JSONObject {
        val o = runCatching { JSONObject(res.text) }.getOrNull()
        if (res.ok && o != null) return o
        val message = o?.optJSONObject("error")?.optString("message").orEmpty().ifEmpty { "HTTP ${res.code}" }
        throw FirebaseException(res.code, message)
    }
}

/**
 * Firestore REST. 문서의 fields(값마다 형식이 붙은 JSON)를 Kotlin 값으로 바꿔 돌려준다.
 * map → Map, array → List, 정수 → Long, 실수 → Double, 문자열·참/거짓 → String·Boolean, null → null.
 */
object FirestoreRest {
    private const val BASE =
        "https://firestore.googleapis.com/v1/projects/${FirebaseConfig.PROJECT_ID}/databases/(default)/documents"

    /** 문서를 읽는다. 없으면 null. 권한이 없으면 FirebaseException(403) */
    fun get(idToken: String, path: String): Map<String, Any?>? {
        val res = Http.get("$BASE/$path", mapOf("Authorization" to "Bearer $idToken"))
        if (res.code == 404) return null
        if (!res.ok) throw error(res)
        return decodeFields(JSONObject(res.text).optJSONObject("fields"))
    }

    /** 컬렉션의 문서들을 읽는다: 문서 ID → 필드. 컬렉션이 비어 있으면 빈 지도 */
    fun list(idToken: String, path: String): Map<String, Map<String, Any?>> {
        val res = Http.get("$BASE/$path?pageSize=100", mapOf("Authorization" to "Bearer $idToken"))
        if (!res.ok) throw error(res)
        val docs = JSONObject(res.text).optJSONArray("documents") ?: return emptyMap()
        return (0 until docs.length()).associate { i ->
            val doc = docs.getJSONObject(i)
            doc.getString("name").substringAfterLast('/') to decodeFields(doc.optJSONObject("fields"))
        }
    }

    /**
     * 문서 하나에 대한 merge 쓰기 (웹의 batch.set(ref, data, { merge: true })와 같다).
     * set으로 넣은 필드만 updateMask에 넣으므로 다른 필드는 그대로 둔다. 값이 null이면 그 필드를 지운다 (웹의 deleteField()).
     * 문서가 없으면 만든다.
     */
    class Write(val path: String) {
        internal val fields = LinkedHashMap<String, Any?>()
        internal val mask = mutableListOf<String>()

        /** path: 필드 경로 칸들 (예: items, fe-1, level) */
        fun set(vararg path: String, value: Any?): Write {
            mask.add(path.joinToString(".") { fieldName(it) })
            if (value != null) {
                var map = fields
                for (key in path.dropLast(1)) {
                    @Suppress("UNCHECKED_CAST")
                    map = map.getOrPut(key) { LinkedHashMap<String, Any?>() } as LinkedHashMap<String, Any?>
                }
                map[path.last()] = value
            }
            return this
        }

        internal fun toJson(): JSONObject = JSONObject()
            .put(
                "update",
                JSONObject()
                    .put("name", "projects/${FirebaseConfig.PROJECT_ID}/databases/(default)/documents/$path")
                    .put("fields", encodeFields(fields)),
            )
            .put("updateMask", JSONObject().put("fieldPaths", JSONArray(mask)))
    }

    /** 여러 문서 쓰기를 한 번에 보낸다 (웹의 writeBatch). 모두 되거나 모두 안 된다 */
    fun commit(idToken: String, writes: List<Write>) {
        val list = writes.filter { it.mask.isNotEmpty() }
        if (list.isEmpty()) return
        val body = JSONObject().put("writes", JSONArray().apply { list.forEach { put(it.toJson()) } })
        val res = Http.sendJson("POST", "$BASE:commit", body.toString(), mapOf("Authorization" to "Bearer $idToken"))
        if (!res.ok) throw error(res)
    }

    private fun error(res: Http.Response): FirebaseAuthRest.FirebaseException {
        val message = runCatching { JSONObject(res.text).optJSONObject("error")?.optString("status") }.getOrNull()
        return FirebaseAuthRest.FirebaseException(res.code, message?.ifEmpty { null } ?: "HTTP ${res.code}")
    }

    private fun encodeFields(map: Map<String, Any?>): JSONObject {
        val o = JSONObject()
        for ((k, v) in map) if (v != null) o.put(k, encodeValue(v))
        return o
    }

    /** 필드 경로의 한 칸. 영문·숫자·밑줄이 아니면(예: fe-1) 역따옴표로 감싼다 */
    internal fun fieldName(name: String): String =
        if (Regex("[A-Za-z_][A-Za-z_0-9]*").matches(name)) name
        else "`" + name.replace("\\", "\\\\").replace("`", "\\`") + "`"

    private fun encodeValue(value: Any): JSONObject = when (value) {
        is String -> JSONObject().put("stringValue", value)
        is Boolean -> JSONObject().put("booleanValue", value)
        is Int, is Long -> JSONObject().put("integerValue", value.toString())
        is Double, is Float -> JSONObject().put("doubleValue", (value as Number).toDouble())
        is Map<*, *> -> JSONObject().put(
            "mapValue",
            JSONObject().put("fields", encodeFields(value.entries.associate { (k, v) -> k.toString() to v })),
        )
        else -> throw IllegalArgumentException("Firestore에 쓸 수 없는 값: $value")
    }

    fun decodeFields(fields: JSONObject?): Map<String, Any?> {
        if (fields == null) return emptyMap()
        return fields.keys().asSequence().associateWith { decodeValue(fields.getJSONObject(it)) }
    }

    fun decodeValue(v: JSONObject): Any? = when {
        v.has("mapValue") -> decodeFields(v.getJSONObject("mapValue").optJSONObject("fields"))
        v.has("arrayValue") -> v.getJSONObject("arrayValue").optJSONArray("values").orEmpty().map(::decodeValue)
        v.has("stringValue") -> v.getString("stringValue")
        v.has("integerValue") -> v.getString("integerValue").toLong()
        v.has("doubleValue") -> v.getDouble("doubleValue")
        v.has("booleanValue") -> v.getBoolean("booleanValue")
        v.has("timestampValue") -> v.getString("timestampValue")
        else -> null // nullValue 등
    }

    private fun JSONArray?.orEmpty(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}
