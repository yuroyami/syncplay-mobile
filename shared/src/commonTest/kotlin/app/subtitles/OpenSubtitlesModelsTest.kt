package app.subtitles

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the OpenSubtitles .com wire models against response shapes from the official docs
 * (https://opensubtitles.stoplight.io/docs/opensubtitles-api). The real API returns many
 * more attribute keys than we model — `ignoreUnknownKeys` must absorb them.
 */
class OpenSubtitlesModelsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `search response decodes the documented shape`() {
        val sample = """
        {
          "total_pages": 5, "total_count": 250, "per_page": 50, "page": 1,
          "data": [{
            "id": "9000", "type": "subtitle",
            "attributes": {
              "subtitle_id": "9000", "language": "en",
              "download_count": 697844, "new_download_count": 1204,
              "hearing_impaired": false, "hd": true, "fps": 23.976, "votes": 4, "ratings": 6.0,
              "from_trusted": true, "foreign_parts_only": false,
              "ai_translated": false, "machine_translated": false,
              "upload_date": "2010-01-13T20:57:33Z",
              "release": "Movie.Name.2009.720p.BluRay.x264",
              "comments": "", "legacy_subtitle_id": 3506746,
              "uploader": {"uploader_id": 47823, "name": "someone", "rank": "Gold member"},
              "feature_details": {"feature_id": 38367, "feature_type": "Movie", "year": 2009,
                "title": "Movie Name", "movie_name": "2009 - Movie Name", "imdb_id": 1130884, "tmdb_id": 11324},
              "url": "https://www.opensubtitles.com/en/subtitles/legacy/3506746",
              "files": [{"file_id": 1923552, "cd_number": 1, "file_name": "Movie.Name.2009.720p.srt"}]
            }
          }]
        }
        """.trimIndent()

        val decoded = json.decodeFromString<OpenSubtitlesSearchResponse>(sample)
        assertEquals(250, decoded.totalCount)
        val attrs = decoded.data.single().attributes
        assertEquals("en", attrs.language)
        assertEquals(697844, attrs.downloadCount)
        assertEquals(true, attrs.fromTrusted)
        assertEquals(1923552, attrs.files.single().fileId)
        assertEquals("Movie.Name.2009.720p.srt", attrs.files.single().fileName)
    }

    @Test
    fun `download request encodes as the documented POST body`() {
        val body = json.encodeToString(OpenSubtitlesDownloadRequest.serializer(), OpenSubtitlesDownloadRequest(fileId = 1923552))
        assertEquals("""{"file_id":1923552}""", body)
    }

    @Test
    fun `download response decodes link and quota fields`() {
        val sample = """
        {
          "link": "https://www.opensubtitles.com/download/ABC123/subtitle.srt",
          "file_name": "subtitle.srt",
          "requests": 3, "remaining": 97,
          "message": "Your quota will be renewed in 12 hours",
          "reset_time": "12 hours", "reset_time_utc": "2026-06-13T00:00:00.000Z"
        }
        """.trimIndent()

        val decoded = json.decodeFromString<OpenSubtitlesDownloadResponse>(sample)
        assertEquals("subtitle.srt", decoded.fileName)
        assertEquals(97, decoded.remaining)
        assertEquals("https://www.opensubtitles.com/download/ABC123/subtitle.srt", decoded.link)
    }
}
