package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워크스페이스의 아이템 활동(생성·처리완료·수정·삭제·즐겨찾기·휴지통) 발생 여부 판정.
 * 이벤트별로 별도 로그를 두지 않고 Item.updatedAt(BaseTimeEntity의 auditing 컬럼)이
 * 위 모든 변경에서 함께 갱신된다는 점을 이용한다 — 이벤트 종류를 구분해 저장할 필요가 없다.
 */
@Service
public class ItemActivityService {

    private final ItemRepository itemRepository;

    public ItemActivityService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasActivitySince(Long workspaceId, OffsetDateTime since) {
        return false;
    }
}
