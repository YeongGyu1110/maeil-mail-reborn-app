package com.maeilmail.app.data

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 받아 둔 질문 파일. 저장소와 같은 경로로 둔다 (filesDir/content/frontend/contents/fe-1.md).
 * manifest.json에는 마지막으로 맞춘 커밋과 시각만 둔다. 파일이 바뀌었는지는 파일 내용의 git blob sha로 직접 비교한다.
 */
class ContentStore(filesDir: File) {
    private val root = File(filesDir, "content")
    private val manifestFile = File(root, "manifest.json")

    /** tracked: 그때 받던 파일 종류의 판 (ContentSync.TRACKED_VERSION). 받는 파일이 늘면 같은 커밋이어도 다시 비교한다 */
    data class Manifest(val commit: String, val syncedAt: Long, val tracked: Int = 0)

    fun file(path: String): File = File(root, path)

    fun read(path: String): String? = file(path).takeIf { it.isFile }?.readText()

    fun readBytes(path: String): ByteArray? = file(path).takeIf { it.isFile }?.readBytes()

    /** 받아 둔 파일의 git blob sha (없으면 null) */
    fun blobSha(path: String): String? = readBytes(path)?.let(::gitBlobSha)

    /** 받아 둔 모든 파일의 저장소 경로 */
    fun paths(): List<String> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it != manifestFile && !it.name.endsWith(".tmp") }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toList()
    }

    /** 다른 곳에서 읽는 중에 반쯤 쓴 파일이 보이지 않도록 임시 파일에 쓰고 바꿔 넣는다 */
    fun write(path: String, bytes: ByteArray) {
        val target = file(path)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw java.io.IOException("파일을 저장하지 못했습니다: $path")
        }
    }

    fun delete(path: String) {
        file(path).delete()
    }

    fun manifest(): Manifest? = runCatching {
        val o = JSONObject(manifestFile.readText())
        Manifest(o.getString("commit"), o.getLong("syncedAt"), o.optInt("tracked", 0))
    }.getOrNull()

    fun saveManifest(manifest: Manifest) {
        write(
            manifestFile.relativeTo(root).path,
            JSONObject().put("commit", manifest.commit).put("syncedAt", manifest.syncedAt).put("tracked", manifest.tracked)
                .toString().toByteArray(),
        )
    }

    /** 받아 둔 목차로 질문 전체를 만든다. 목차가 없으면 빈 목록 */
    fun loadCatalog(): Catalog {
        val questions = ArrayList<Question>()
        val categories = ArrayList<String>()
        for (area in AREAS) {
            val toc = read("${area.dir}/toc.md") ?: continue
            val (q, c) = Parsers.parseArea(area, toc, read("${area.dir}/toc-category.md").orEmpty())
            questions += q
            categories += c
        }
        val synonyms = read(Source.SYNONYMS_PATH)?.let(Parsers::parseSynonyms).orEmpty()
        return Catalog(questions, categories, synonyms)
    }
}

/** git이 파일 내용을 가리키는 값 (sha1("blob <크기>\0" + 내용)). GitHub 트리의 sha와 같다 */
fun gitBlobSha(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-1")
    md.update("blob ${bytes.size}\u0000".toByteArray())
    md.update(bytes)
    return md.digest().joinToString("") { "%02x".format(it) }
}
