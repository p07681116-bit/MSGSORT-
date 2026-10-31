package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.SortCodeItem
import com.example.util.SecurityUtils
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MsgSort", appName)
  }

  @Test
  fun `sortCode item image properties and security`() {
    val future = Date(System.currentTimeMillis() + 3600 * 1000)
    val item = SortCodeItem(
      code = "IMG123",
      ownerUid = "user_abc",
      title = "Scan of whiteboard",
      contentType = "image",
      fileName = "whiteboard.jpg",
      fileSize = 204800L,
      fileMimeType = "image/jpeg",
      storagePath = "images/IMG123_whiteboard.jpg",
      imageUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/images%2FIMG123.jpg",
      imageWidth = 1920,
      imageHeight = 1080,
      expiresAt = Timestamp(future),
      status = "active",
      passwordProtected = true,
      passwordHash = SecurityUtils.sha256("SecretPass123"),
      downloadAllowed = true
    )

    // Verify properties
    assertEquals("image", item.contentType)
    assertEquals("", item.fileBase64)
    assertEquals("", item.thumbnailBase64)
    assertEquals(1920, item.imageWidth)
    assertEquals(1080, item.imageHeight)
    assertTrue(item.isAccessible())
    assertFalse(item.isExpired())
    assertFalse(item.isRevoked())
    assertEquals("active", item.getComputedStatus())

    // Verify password verification
    assertEquals(item.passwordHash, SecurityUtils.sha256("SecretPass123"))
    assertFalse(item.passwordHash == SecurityUtils.sha256("WrongPass"))
  }

  @Test
  fun `sortCode expiry and revocation statuses`() {
    val past = Date(System.currentTimeMillis() - 3600 * 1000)
    val expiredItem = SortCodeItem(
      code = "EXP001",
      expiresAt = Timestamp(past),
      status = "active"
    )
    assertTrue(expiredItem.isExpired())
    assertFalse(expiredItem.isAccessible())
    assertEquals("expired", expiredItem.getComputedStatus())

    val revokedItem = SortCodeItem(
      code = "REV001",
      expiresAt = Timestamp(Date(System.currentTimeMillis() + 3600 * 1000)),
      status = "revoked"
    )
    assertTrue(revokedItem.isRevoked())
    assertFalse(revokedItem.isAccessible())
    assertEquals("revoked", revokedItem.getComputedStatus())
  }

  @Test
  fun `generateSortCode produces valid 6-char alphanumeric code`() {
    val code = SecurityUtils.generateSortCode()
    assertTrue(code.startsWith("MS-"))
    val body = code.removePrefix("MS-")
    assertEquals(6, body.length)
    assertTrue(body.all { it.isLetterOrDigit() })
    assertTrue(SecurityUtils.isValidSortCodeFormat(code))
  }

  @Test
  fun `pdf and document SortCode metadata and accessibility`() {
    val future = Date(System.currentTimeMillis() + 7200 * 1000)
    val docItem = SortCodeItem(
      code = "DOC456",
      ownerUid = "user_writer",
      title = "Quarterly Report",
      contentType = "document",
      fileName = "report.pdf",
      fileSize = 1048576L, // 1 MB
      fileMimeType = "application/pdf",
      storagePath = "files/DOC456_report.pdf",
      fileUrl = "https://firebasestorage.googleapis.com/v0/b/mesege-sort.firebasestorage.app/o/files%2FDOC456_report.pdf?alt=media&token=tok-123",
      expiresAt = Timestamp(future),
      status = "active",
      passwordProtected = false,
      downloadAllowed = true
    )

    assertEquals("document", docItem.contentType)
    assertEquals("report.pdf", docItem.fileName)
    assertEquals("application/pdf", docItem.fileMimeType)
    assertEquals(1048576L, docItem.fileSize)
    assertTrue(docItem.isAccessible())
    assertFalse(docItem.isExpired())
    assertFalse(docItem.isRevoked())
    assertTrue(docItem.fileUrl.startsWith("https://firebasestorage.googleapis.com"))
  }

  @Test
  fun `video SortCode metadata and accessibility`() {
    val future = Date(System.currentTimeMillis() + 86400 * 1000)
    val videoItem = SortCodeItem(
      code = "VID789",
      ownerUid = "user_creator",
      title = "Project Pitch Video",
      contentType = "video",
      fileName = "pitch.mp4",
      fileSize = 15728640L, // 15 MB (< 25 MB limit)
      fileMimeType = "video/mp4",
      storagePath = "videos/VID789_pitch.mp4",
      fileUrl = "https://firebasestorage.googleapis.com/v0/b/mesege-sort.firebasestorage.app/o/videos%2FVID789_pitch.mp4?alt=media&token=tok-456",
      expiresAt = Timestamp(future),
      status = "active",
      passwordProtected = true,
      passwordHash = SecurityUtils.sha256("VidPass2026"),
      downloadAllowed = false
    )

    assertEquals("video", videoItem.contentType)
    assertEquals("pitch.mp4", videoItem.fileName)
    assertEquals("video/mp4", videoItem.fileMimeType)
    assertEquals(15728640L, videoItem.fileSize)
    assertTrue(videoItem.fileSize <= 26214400L) // <= 25 MB
    assertTrue(videoItem.isAccessible())
    assertTrue(videoItem.passwordProtected)
    assertFalse(videoItem.downloadAllowed)
    assertEquals(videoItem.passwordHash, SecurityUtils.sha256("VidPass2026"))
  }

  @Test
  fun `expired and revoked items cannot be accessed`() {
    val past = Date(System.currentTimeMillis() - 1000)
    val expiredMedia = SortCodeItem(
      code = "EXP_MEDIA",
      ownerUid = "user_1",
      contentType = "image",
      storagePath = "images/EXP_MEDIA_test.jpg",
      fileUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/images%2Ftest.jpg",
      expiresAt = Timestamp(past),
      status = "active"
    )
    assertTrue(expiredMedia.isExpired())
    assertFalse(expiredMedia.isAccessible())
    assertEquals("expired", expiredMedia.getComputedStatus())

    val revokedMedia = SortCodeItem(
      code = "REV_MEDIA",
      ownerUid = "user_1",
      contentType = "video",
      storagePath = "videos/REV_MEDIA_test.mp4",
      fileUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/videos%2Ftest.mp4",
      expiresAt = Timestamp(Date(System.currentTimeMillis() + 100000)),
      status = "revoked"
    )
    assertTrue(revokedMedia.isRevoked())
    assertFalse(revokedMedia.isAccessible())
    assertEquals("revoked", revokedMedia.getComputedStatus())
  }
}
