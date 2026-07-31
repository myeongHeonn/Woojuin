package com.ssafy.woojuin.global.sse;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceEventBroadcasterTest {

    @Mock WorkspaceSseRegistry sseRegistry;
    @InjectMocks WorkspaceEventBroadcaster broadcaster;

    @Test
    void 커밋된_도메인_변경을_해당_워크스페이스에_브로드캐스트한다() {
        broadcaster.onWorkspaceChanged(WorkspaceChangedEvent.of(7L, WorkspaceEventType.ITEM));

        verify(sseRegistry).broadcast(7L, WorkspaceEvent.of(WorkspaceEventType.ITEM, 7L));
    }
}
