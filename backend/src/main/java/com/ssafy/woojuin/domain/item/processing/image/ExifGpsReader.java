package com.ssafy.woojuin.domain.item.processing.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import com.ssafy.woojuin.domain.location.GeoPoint;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 사진 EXIF의 GPS 좌표를 읽는다 (FR-023). 외부 호출 없이 파일 메타데이터만 파싱한다.
 *
 * <p>{@link ImageTextExtractor}와 같은 계약 — <b>예외를 던지지 않는다</b>. 좌표가 없는 게
 * 정상이고(대부분의 이미지가 그렇다) 위치는 부가 정보라, 실패는 조용히 흡수한다.
 * {@code catch (Exception)}으로 넓게 잡는 건 잘린 파일에서 metadata-extractor가 unchecked
 * 예외를 던지는 경우까지 덮으려는 것이다.
 *
 * <p>도분초→십진 변환과 {@code GPSLatitudeRef}/{@code GPSLongitudeRef}(남위·서경 부호)는
 * 라이브러리가 처리한다 — <b>직접 계산하지 말 것</b>.
 *
 * <p><b>히트율에 기대하지 말 것.</b> 카카오톡·인스타그램 등 대부분의 메신저·SNS가 업로드
 * 시점에 GPS EXIF를 지우고, 스크린샷엔 애초에 없다. 카메라 롤에서 바로 올린 사진만 걸린다.
 *
 * <p>HEIC/HEIF(아이폰 기본)는 애초에 이 클래스까지 오지 않는다 — 서버가 HEIC를 디코딩하지
 * 못해(scrimage가 썸네일을 못 만드는 것과 같은 한계) 웹 프론트가 업로드 전에 JPEG로 바꾼다.
 * 그때 디코딩 결과물엔 EXIF가 없어서 좌표가 통째로 날아가는데, 프론트가 원본 HEIC에서 GPS만
 * 읽어 결과 JPEG의 EXIF로 다시 심어 준다(frontend {@code utils/heicToJpeg.ts}). 즉 여기 도착하는
 * 아이폰 사진은 <b>GPS 태그만 남은 JPEG</b>이고, 이 클래스는 평소대로 읽으면 된다.
 *
 * <p>다행인 점: {@code S3Uploader}가 멀티파트를 재인코딩 없이 스트리밍하므로 S3 원본에
 * EXIF가 그대로 남는다.
 */
@Slf4j
@Component
public class ExifGpsReader {

    /**
     * @param bytes S3 원본 이미지 바이트 (null 허용)
     * @return 좌표. EXIF에 GPS가 없거나 읽지 못하면 empty
     */
    public Optional<GeoPoint> read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gps == null) {
                return Optional.empty();
            }
            GeoLocation location = gps.getGeoLocation();
            if (location == null || location.isZero()) {
                return Optional.empty();
            }
            // 범위 검증은 GeoPoint.of가 한다.
            return GeoPoint.of(location.getLatitude(), location.getLongitude());
        } catch (Exception e) {
            log.debug("EXIF GPS 읽기 실패(무시): cause={}", e.toString());
            return Optional.empty();
        }
    }
}
