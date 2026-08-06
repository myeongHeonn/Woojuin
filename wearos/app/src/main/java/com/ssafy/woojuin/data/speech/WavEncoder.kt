package com.ssafy.woojuin.data.speech

/**
 * PCM 을 WAV 로 감싼다 — 44바이트 헤더를 앞에 붙이는 것이 전부다.
 *
 * <p>서버(whisper)가 <b>확장자와 헤더로 형식을 판별</b>하므로 헤더 없이 PCM 만 올리면
 * 400 이 돌아온다. 오디오를 압축하지 않는 이유는 워치에 인코더를 붙이면 지연과 코드가
 * 함께 늘어나는데, 손목에서 하는 한마디(3~6초)는 WAV 로도 100~200KB 라 그럴 값이 없다.
 */
object WavEncoder {

    private const val HEADER_SIZE = 44
    private const val PCM_FORMAT = 1.toShort()
    private const val BITS_PER_SAMPLE = 16.toShort()

    /**
     * @param pcm 리틀엔디언 16bit 모노 PCM
     * @param sampleRate 초당 샘플 수 (워치는 [MicRecorder.SAMPLE_RATE])
     */
    fun wrap(pcm: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1.toShort()
        val byteRate = sampleRate * channels * BITS_PER_SAMPLE / 8
        val out = ByteArray(HEADER_SIZE + pcm.size)

        ascii(out, 0, "RIFF")
        // RIFF 청크 크기 = 전체 - 8 (RIFF 네 글자와 이 필드 자신을 뺀다)
        int32(out, 4, HEADER_SIZE - 8 + pcm.size)
        ascii(out, 8, "WAVE")

        ascii(out, 12, "fmt ")
        int32(out, 16, 16) // fmt 청크 본문 크기(PCM 은 16)
        int16(out, 20, PCM_FORMAT)
        int16(out, 22, channels)
        int32(out, 24, sampleRate)
        int32(out, 28, byteRate)
        int16(out, 32, (channels * BITS_PER_SAMPLE / 8).toShort()) // 블록 정렬
        int16(out, 34, BITS_PER_SAMPLE)

        ascii(out, 36, "data")
        int32(out, 40, pcm.size)
        pcm.copyInto(out, HEADER_SIZE)
        return out
    }

    private fun ascii(out: ByteArray, offset: Int, text: String) {
        text.forEachIndexed { index, char -> out[offset + index] = char.code.toByte() }
    }

    /** WAV 는 리틀엔디언이다 — 뒤집으면 재생기·서버가 형식을 못 읽는다. */
    private fun int32(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value shr 8) and 0xFF).toByte()
        out[offset + 2] = ((value shr 16) and 0xFF).toByte()
        out[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun int16(out: ByteArray, offset: Int, value: Short) {
        val intValue = value.toInt()
        out[offset] = (intValue and 0xFF).toByte()
        out[offset + 1] = ((intValue shr 8) and 0xFF).toByte()
    }
}
