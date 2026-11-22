package com.maeilmail.app.account

import android.util.Base64
import com.maeilmail.app.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * 메모 저장소에 메모 파일을 커밋하고 지운다 (웹 memo-core.js의 commitMemo, deleteMemoFile과 같다).
 * 파일은 저장소의 기본 브랜치에 {질문 ID}.md로 둔다. 잔디는 기본 브랜치 커밋만 집계되므로 브랜치를 정하지 않는다.
 * 커밋 작성자는 토큰 주인(로그인한 사용자)이다.
 */
object GitHubApi {
    /** GitHub App을 설치할 저장소를 고르는 페이지 (웹과 같다) */
    const val INSTALL_URL = "https://github.com/apps/maeil-mail-reborn-memo/installations/new"

    /** GitHub API 오류. status: HTTP 응답 코드 */
    class ApiException(val status: Int, message: String) : IOException(message)

    data class Repo(val id: Long, val fullName: String, val description: String)

    /** 메모를 {id}.md로 커밋한다. 새 파일이면 "메모 추가", 있던 파일이면 "메모 수정" */
    suspend fun commitMemo(repo: String, id: String, text: String, title: String) {
        val path = "/repos/$repo/contents/$id.md"
        for (attempt in 0..1) {
            val sha = fileSha(path)
            try {
                val body = JSONObject()
                    .put("message", "${if (sha != null) "메모 수정" else "메모 추가"}: ${id.uppercase()} $title".trim())
                    .put("content", base64("$text\n"))
                if (sha != null) body.put("sha", sha)
                call("PUT", path, body)
                return
            } catch (e: ApiException) {
                // 다른 기기에서 그사이 커밋해 sha가 바뀌었으면 한 번 더 시도한다
                if (e.status != 409 || attempt > 0) throw e
            }
        }
    }

    /**
     * 메모 파일을 지운다 ("메모 삭제" 커밋). 지난 커밋은 저장소 기록에 남는다 (잔디도 그대로).
     * 파일이 없으면(올린 적 없는 메모) 할 일이 없으므로 false
     */
    suspend fun deleteMemoFile(repo: String, id: String, title: String): Boolean {
        val path = "/repos/$repo/contents/$id.md"
        for (attempt in 0..1) {
            val sha = fileSha(path) ?: return false
            try {
                call("DELETE", path, JSONObject().put("message", "메모 삭제: ${id.uppercase()} $title".trim()).put("sha", sha))
                return true
            } catch (e: ApiException) {
                if (e.status != 409 || attempt > 0) throw e
            }
        }
        return false
    }

    /** 앱을 설치한 저장소 중 잔디가 심어지는 것만: 포크·보관 저장소는 뺀다 (웹 listInstalledRepos와 같다) */
    suspend fun installedRepos(): List<Repo> {
        val installations = call("GET", "/user/installations?per_page=100").getJSONArray("installations")
        val repos = mutableListOf<Repo>()
        for (i in 0 until installations.length()) {
            val installation = installations.getJSONObject(i).getLong("id")
            for (page in 1..10) {
                val list = call("GET", "/user/installations/$installation/repositories?per_page=100&page=$page")
                    .getJSONArray("repositories")
                for (j in 0 until list.length()) {
                    val r = list.getJSONObject(j)
                    if (r.optBoolean("fork") || r.optBoolean("archived")) continue
                    repos.add(Repo(r.getLong("id"), r.getString("full_name"), r.optString("description").takeIf { it != "null" }.orEmpty()))
                }
                if (list.length() < 100) break
            }
        }
        return repos
    }

    /** 파일의 sha. 파일이 없으면(또는 빈 저장소면) null */
    private suspend fun fileSha(path: String): String? = try {
        call("GET", path).optString("sha").ifEmpty { null }
    } catch (e: ApiException) {
        if (e.status == 404) null else throw e
    }

    /** GitHub API를 부른다. 401이면 토큰을 한 번 새로 받아 다시 부른다 (만료되는 토큰일 때) */
    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject {
        var token = Account.githubToken()
        for (attempt in 0..1) {
            val headers = buildMap {
                put("Accept", "application/vnd.github+json")
                put("Authorization", "Bearer $token")
                put("X-GitHub-Api-Version", "2022-11-28")
                if (body != null) put("Content-Type", "application/json; charset=utf-8")
            }
            val res = withContext(Dispatchers.IO) {
                Http.request(method, "https://api.github.com$path", headers, body?.toString()?.toByteArray())
            }
            if (res.code == 401 && attempt == 0) {
                token = Account.githubToken(rejected = token)
                continue
            }
            if (!res.ok) {
                val message = runCatching { JSONObject(res.text).optString("message") }.getOrNull().orEmpty()
                throw ApiException(res.code, message.ifEmpty { "GitHub API ${res.code}" })
            }
            val text = res.text.trim()
            return when {
                text.startsWith("{") -> JSONObject(text)
                text.startsWith("[") -> JSONObject().put("items", JSONArray(text))
                else -> JSONObject()
            }
        }
        throw ApiException(401, "GitHub 연결이 만료됐어요")
    }

    private fun base64(text: String): String = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /** GitHub 오류 → 화면에 보일 문구 (웹 githubErrorMessage와 같다) */
    fun errorMessage(e: Throwable): String = when {
        e is Account.NotSignedIn -> "GitHub 연결이 만료됐어요. 설정에서 다시 로그인해 주세요."
        e is ApiException && e.status == 401 -> "GitHub 연결이 만료됐어요. 설정에서 다시 로그인해 주세요."
        e is ApiException && (e.status == 429 || e.message?.contains("rate limit", ignoreCase = true) == true) ->
            "GitHub 요청 한도에 걸렸어요. 잠시 후 다시 저장해 주세요."
        e is ApiException && (e.status == 403 || e.status == 404) -> "저장소에 접근할 수 없어요. 앱에 저장소가 추가돼 있는지 확인해 주세요."
        isNetworkError(e) -> "인터넷에 연결되지 않았어요."
        else -> "(${e.message})"
    }
}
