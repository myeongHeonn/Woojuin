package com.ssafy.woojuin.domain.integration.service;

import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.integration.dto.ChannelMappingRequest;
import com.ssafy.woojuin.domain.integration.dto.ChannelMappingResponse;
import com.ssafy.woojuin.domain.integration.entity.ChatChannelMapping;
import com.ssafy.woojuin.domain.integration.exception.ChannelMappingConflictException;
import com.ssafy.woojuin.domain.integration.repository.ChatChannelMappingRepository;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceMemberRequiredException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChannelMappingService {
    private final ChatChannelMappingRepository mappingRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;

    public ChannelMappingService(ChatChannelMappingRepository mappingRepository,
            WorkspaceMemberRepository memberRepository, UserRepository userRepository) {
        this.mappingRepository = mappingRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ChannelMappingResponse> list(Long userId) {
        return mappingRepository.findByCreatedByIdOrderByIdAsc(userId).stream()
                .map(ChannelMappingResponse::from)
                .toList();
    }

    @Transactional
    public ChannelMappingResponse create(Long userId, ChannelMappingRequest request) {
        WorkspaceMember membership = memberRepository
                .findByWorkspaceIdAndUserId(request.workspaceId(), userId)
                .orElseThrow(() -> new WorkspaceMemberRequiredException(request.workspaceId()));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (mappingRepository.findByPlatformAndChannelId(request.platform(), request.channelId()).isPresent()) {
            throw new ChannelMappingConflictException();
        }
        try {
            return ChannelMappingResponse.from(mappingRepository.saveAndFlush(new ChatChannelMapping(
                    request.platform(), request.channelId(), membership.getWorkspace(), user)));
        } catch (DataIntegrityViolationException e) {
            throw new ChannelMappingConflictException();
        }
    }
}
