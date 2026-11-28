package com.maeilmail.app.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.maeilmail.app.data.Http
import java.io.File

/**
 * 로그인한 사람의 GitHub 프로필 사진 (웹 상단 바의 사진과 같다).
 * 누구나 볼 수 있는 주소라 로그인 토큰을 보내지 않는다. 받은 사진은 캐시에 두어 인터넷 없이도 보이고, 하루가 지나면 새로 받는다.
 * 파일을 읽고 받으므로 메인 스레드 밖에서 부른다.
 */
object Avatar {
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private const val MAX_BYTES = 1_000_000

    /** 설정에 36dp로 보인다. 화면이 촘촘해도 흐리지 않게 넉넉히 받는다 */
    private const val SIZE = 160

    private fun file(context: Context, githubId: String) = File(context.cacheDir, "avatar-$githubId")

    /** 받아 둔 사진 (없으면 null) */
    fun cached(context: Context, githubId: String): Bitmap? =
        file(context, githubId).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }

    /** 받아 둔 지 하루가 지났거나 없으면 새로 받는다. 받지 않았거나 받지 못했으면 null */
    fun refresh(context: Context, githubId: String): Bitmap? {
        val file = file(context, githubId)
        if (file.isFile && System.currentTimeMillis() - file.lastModified() in 0 until MAX_AGE_MS) return null
        return runCatching {
            val response = Http.get("https://avatars.githubusercontent.com/u/$githubId?s=$SIZE", maxBytes = MAX_BYTES)
            if (!response.ok) return null
            // 사진으로 읽히는 것만 남긴다. 반쯤 쓴 파일이 남지 않게 임시 파일에 쓰고 바꿔 넣는다
            val bitmap = BitmapFactory.decodeByteArray(response.bytes, 0, response.bytes.size) ?: return null
            val tmp = File(context.cacheDir, "${file.name}.tmp")
            tmp.writeBytes(response.bytes)
            if (!tmp.renameTo(file)) tmp.delete()
            bitmap
        }.getOrNull()
    }

    /** 로그아웃하면 받아 둔 사진을 지운다 */
    fun clear(context: Context) {
        context.cacheDir.listFiles { f -> f.name.startsWith("avatar-") }?.forEach(File::delete)
    }
}
