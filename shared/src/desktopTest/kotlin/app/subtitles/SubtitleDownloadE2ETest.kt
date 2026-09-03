package app.subtitles

import app.utils.readFile
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Live end-to-end probe of the OpenSubtitles search+download pipeline (network required).
 * Exercises the exact commonMain code path the in-app subtitle search uses, including the
 * app's only Ktorfit @Body POST — the call that silently died with "Fail to prepare request
 * body for sending / Content-Type: null" until requestDownload declared its Content-Type.
 *
 * @Ignore because it needs network and consumes 1 unit of the key's daily download quota
 * (5/day free) per run. Remove the annotation to re-verify the pipeline manually.
 */
class SubtitleDownloadE2ETest {

    @Ignore
    @Test
    fun searchThenDownload() = runBlocking {
        val results = when (val outcome = SubtitleSearch.search("big buck bunny", "en")) {
            is SubtitleSearchOutcome.Results -> outcome.items
            is SubtitleSearchOutcome.Failed -> error("search failed: ${outcome.reason}")
        }
        println("E2E: search returned ${results.size} results")
        check(results.isNotEmpty()) { "search returned no results" }

        val first = results.first()
        println("E2E: downloading fileId=${first.fileId} (${first.filename})")

        when (val outcome = SubtitleSearch.download(first.fileId)) {
            is SubtitleDownloadResult.Success -> {
                println("E2E: SUCCESS path=${outcome.path} remaining=${outcome.remaining}")
                val content = readFile(outcome.path)
                println("E2E: file head: ${content.take(120).replace("\n", "\\n")}")
                check(content.isNotBlank()) { "downloaded subtitle file is empty" }
            }
            is SubtitleDownloadResult.QuotaExceeded ->
                error("quota exceeded (resets in ${outcome.resetTime})")
            SubtitleDownloadResult.Failed ->
                error("download FAILED — check loggy output above for the real cause")
        }
    }
}
