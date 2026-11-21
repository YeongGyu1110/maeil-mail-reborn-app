package com.maeilmail.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * 웹 서비스 저장소에서 질문 파일을 받아 ContentStore에 맞춘다.
 *
 * 1. 기본 브랜치의 최신 커밋을 묻는다 (GitHub API). 지난번과 같으면 끝.
 * 2. 그 커밋의 파일 목록(트리)과 각 파일의 sha를 받아, 받아 둔 파일과 sha가 다른 것만 받는다.
 *    파일은 커밋을 고정한 raw 주소로 받아 CDN 캐시 때문에 옛 내용을 받지 않게 하고, 받은 내용의 sha를 트리와 대조한다.
 * 3. 저장소에서 없어진 파일은 지운다.
 *
 * GitHub API는 로그인 없이 시간당 60번까지라(한 번 맞출 때 2번 쓴다), 한도에 걸리면 raw 주소만으로 목차와
 * 아직 없는 답변을 받는다 (이미 받은 답변의 수정은 다음에 API로 맞출 때 받는다).
 */
class ContentSync(private val store: ContentStore) {

    sealed interface Result {
        /** 바뀐 것 없음 */
        data object UpToDate : Result
        data class Updated(val downloaded: Int, val removed: Int) : Result
    }

    /** 받을 파일과 지울 파일 */
    data class Plan(val download: List<String>, val remove: List<String>)

    suspend fun sync(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Result = withContext(Dispatchers.IO) {
        val commit = try {
            latestCommit()
        } catch (e: Http.StatusException) {
            if (e.code == 403 || e.code == 429 || e.code >= 500) return@withContext syncFromRawBranch(onProgress)
            throw e
        }
        val manifest = store.manifest()
        if (manifest?.commit == commit) {
            store.saveManifest(manifest.copy(syncedAt = System.currentTimeMillis()))
            return@withContext Result.UpToDate
        }

        val remote = try {
            tree(commit)
        } catch (e: Http.StatusException) {
            if (e.code == 403 || e.code == 429 || e.code >= 500) return@withContext syncFromRawBranch(onProgress)
            throw e
        }
        val local = store.paths().filter(Source::isTracked).associateWith { store.blobSha(it).orEmpty() }
        val plan = plan(remote, local)

        val files = download(plan.download, ref = commit, expected = remote, onProgress = onProgress)
        writeAll(files)
        plan.remove.forEach(store::delete)
        store.saveManifest(ContentStore.Manifest(commit, System.currentTimeMillis()))
        if (plan.download.isEmpty() && plan.remove.isEmpty()) Result.UpToDate
        else Result.Updated(plan.download.size, plan.remove.size)
    }

    private fun latestCommit(): String {
        val res = Http.getOk(
            "https://api.github.com/repos/${Source.OWNER}/${Source.REPO}/git/ref/heads/${Source.BRANCH}",
            API_HEADERS,
        )
        return JSONObject(res.text).getJSONObject("object").getString("sha")
    }

    /** 커밋의 파일 중 앱이 받는 파일: 경로 → blob sha */
    private fun tree(commit: String): Map<String, String> {
        val res = Http.getOk(
            "https://api.github.com/repos/${Source.OWNER}/${Source.REPO}/git/trees/$commit?recursive=1",
            API_HEADERS,
        )
        val json = JSONObject(res.text)
        if (json.optBoolean("truncated")) throw IOException("저장소 파일 목록이 너무 커서 다 받지 못했습니다")
        val entries = json.getJSONArray("tree")
        val result = LinkedHashMap<String, String>()
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            val path = e.getString("path")
            if (e.getString("type") == "blob" && Source.isTracked(path)) result[path] = e.getString("sha")
        }
        return result
    }

    /** API 한도에 걸렸을 때: 기본 브랜치의 raw 주소로 목차·동의어는 늘, 답변은 아직 없는 것만 받는다 */
    private suspend fun syncFromRawBranch(onProgress: (Int, Int) -> Unit): Result {
        val indexPaths = AREAS.flatMap { listOf("${it.dir}/toc.md", "${it.dir}/toc-category.md") } + Source.SYNONYMS_PATH
        val index = download(indexPaths, ref = Source.BRANCH, expected = null, onProgress = { _, _ -> })
        val ids = AREAS.flatMap { area ->
            val toc = index["${area.dir}/toc.md"]?.toString(Charsets.UTF_8).orEmpty()
            Parsers.parseArea(area, toc, "").first.map { it.contentPath }
        }
        val missing = ids.filter { store.readBytes(it) == null }
        val contents = download(missing, ref = Source.BRANCH, expected = null, onProgress = onProgress)
        writeAll(contents + index)
        // 커밋을 모르므로 비워 둔다: 다음에 API로 맞출 때 모든 파일의 sha를 비교한다
        store.saveManifest(ContentStore.Manifest("", System.currentTimeMillis()))
        return Result.Updated(contents.size, 0)
    }

    /** 여러 파일을 동시에 받는다 (최대 6개씩). expected가 있으면 받은 내용의 sha를 대조한다 */
    private suspend fun download(
        paths: List<String>,
        ref: String,
        expected: Map<String, String>?,
        onProgress: (Int, Int) -> Unit,
    ): Map<String, ByteArray> = coroutineScope {
        val gate = Semaphore(6)
        val done = AtomicInteger(0)
        onProgress(0, paths.size)
        paths.map { path ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val bytes = Http.getOk("https://raw.githubusercontent.com/${Source.OWNER}/${Source.REPO}/$ref/$path").bytes
                    val sha = expected?.get(path)
                    if (sha != null && gitBlobSha(bytes) != sha) throw IOException("받은 파일이 저장소와 다릅니다: $path")
                    onProgress(done.incrementAndGet(), paths.size)
                    path to bytes
                }
            }
        }.awaitAll().toMap()
    }

    /** 답변을 먼저, 목차를 나중에 쓴다. 도중에 멈춰도 목차에 있는데 답변이 없는 질문이 덜 생기도록 */
    private fun writeAll(files: Map<String, ByteArray>) {
        files.entries.sortedBy { (path, _) -> if ("/contents/" in path) 0 else 1 }
            .forEach { (path, bytes) -> store.write(path, bytes) }
    }

    companion object {
        private val API_HEADERS = mapOf(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
        )

        /** 저장소(remote)와 받아 둔 파일(local)의 sha를 비교해 받을 파일과 지울 파일을 고른다 */
        fun plan(remote: Map<String, String>, local: Map<String, String>): Plan = Plan(
            download = remote.filter { (path, sha) -> local[path] != sha }.keys.toList(),
            remove = local.keys.filter { it !in remote },
        )
    }
}
