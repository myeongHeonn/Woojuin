package com.ssafy.woojuin.data.speech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * WAV 헤더를 고정한다. 틀리면 서버가 400 을 주는데, 워치에서는 "저장에 실패했어요"로만
 * 보여서 원인이 헤더라는 것을 알 길이 없다 — 그래서 여기서 잡는다.
 */
class WavEncoderTest {

    private fun int32At(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun int16At(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun asciiAt(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII)

    @Test
    fun `RIFF WAVE fmt data 순서로 쓴다`() {
        val wav = WavEncoder.wrap(ByteArray(8), 16_000)

        assertEquals("RIFF", asciiAt(wav, 0, 4))
        assertEquals("WAVE", asciiAt(wav, 8, 4))
        assertEquals("fmt ", asciiAt(wav, 12, 4))
        assertEquals("data", asciiAt(wav, 36, 4))
    }

    @Test
    fun `헤더는 44바이트고 PCM 이 그 뒤에 그대로 붙는다`() {
        val pcm = byteArrayOf(1, 2, 3, 4, 5, 6)

        val wav = WavEncoder.wrap(pcm, 16_000)

        assertEquals(44 + pcm.size, wav.size)
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }

    /** RIFF 크기는 "전체 - 8"이다 — 여길 틀리면 재생기가 파일이 깨졌다고 본다. */
    @Test
    fun `RIFF 크기와 data 크기를 맞게 쓴다`() {
        val pcm = ByteArray(100)

        val wav = WavEncoder.wrap(pcm, 16_000)

        assertEquals(36 + pcm.size, int32At(wav, 4))
        assertEquals(pcm.size, int32At(wav, 40))
    }

    @Test
    fun `16kHz 모노 16bit 로 표기한다`() {
        val wav = WavEncoder.wrap(ByteArray(4), 16_000)

        assertEquals(1, int16At(wav, 20)) // PCM
        assertEquals(1, int16At(wav, 22)) // 모노
        assertEquals(16_000, int32At(wav, 24))
        assertEquals(16, int16At(wav, 34)) // bit 수
    }

    /** byteRate = 초당 바이트. 이게 어긋나면 재생 속도가 달라져 인식도 망가진다. */
    @Test
    fun `초당 바이트와 블록 정렬이 샘플레이트와 맞는다`() {
        val wav = WavEncoder.wrap(ByteArray(4), 16_000)

        assertEquals(16_000 * 2, int32At(wav, 28))
        assertEquals(2, int16At(wav, 32))
    }

    @Test
    fun `샘플레이트를 바꾸면 헤더에 반영된다`() {
        val wav = WavEncoder.wrap(ByteArray(4), 8_000)

        assertEquals(8_000, int32At(wav, 24))
        assertEquals(8_000 * 2, int32At(wav, 28))
    }

    @Test
    fun `빈 PCM 도 유효한 헤더를 만든다`() {
        val wav = WavEncoder.wrap(ByteArray(0), 16_000)

        assertEquals(44, wav.size)
        assertEquals(0, int32At(wav, 40))
    }
}
