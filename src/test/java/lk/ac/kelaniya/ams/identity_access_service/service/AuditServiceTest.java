package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.response.AuditEventResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEvent;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import lk.ac.kelaniya.ams.identity_access_service.repository.AuditEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    @DisplayName("record persists AuditEvent with all fields properly mapped")
    void testRecord_persistsAuditEventWithAllFields() {
        UUID subjectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        Instant now = Instant.now();

        AuditEvent savedMock = AuditEvent.builder()
                .id(auditId)
                .eventType(AuditEventType.ACCOUNT_STATUS_CHANGED)
                .subjectUserId(subjectId)
                .actorUserId(actorId)
                .oldValue("PENDING_VERIFICATION")
                .newValue("ACTIVE")
                .reason("Verified identity documents")
                .createdAt(now)
                .build();

        given(auditEventRepository.save(any(AuditEvent.class))).willReturn(savedMock);

        AuditEvent result = auditService.record(
                AuditEventType.ACCOUNT_STATUS_CHANGED,
                subjectId,
                actorId,
                "PENDING_VERIFICATION",
                "ACTIVE",
                "Verified identity documents"
        );

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(auditId);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent captured = captor.getValue();

        assertThat(captured.getEventType()).isEqualTo(AuditEventType.ACCOUNT_STATUS_CHANGED);
        assertThat(captured.getSubjectUserId()).isEqualTo(subjectId);
        assertThat(captured.getActorUserId()).isEqualTo(actorId);
        assertThat(captured.getOldValue()).isEqualTo("PENDING_VERIFICATION");
        assertThat(captured.getNewValue()).isEqualTo("ACTIVE");
        assertThat(captured.getReason()).isEqualTo("Verified identity documents");
    }

    @Test
    @DisplayName("searchAuditLogs queries repository with specification and transforms to DTO page")
    void testSearchAuditLogs_queriesRepositoryAndTransforms() {
        UUID subjectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        Instant now = Instant.now();

        AuditEvent event = AuditEvent.builder()
                .id(auditId)
                .eventType(AuditEventType.ROLE_ASSIGNED)
                .subjectUserId(subjectId)
                .actorUserId(actorId)
                .oldValue(null)
                .newValue("FINANCE_OFFICER")
                .reason(null)
                .createdAt(now)
                .build();

        Page<AuditEvent> eventPage = new PageImpl<>(List.of(event));
        given(auditEventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(eventPage);

        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditEventResponse> result = auditService.searchAuditLogs(
                AuditEventType.ROLE_ASSIGNED,
                subjectId,
                actorId,
                now.minusSeconds(3600),
                now,
                pageable
        );

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        AuditEventResponse response = result.getContent().get(0);
        assertThat(response.getId()).isEqualTo(auditId);
        assertThat(response.getEventType()).isEqualTo(AuditEventType.ROLE_ASSIGNED);
        assertThat(response.getSubjectUserId()).isEqualTo(subjectId);
        assertThat(response.getActorUserId()).isEqualTo(actorId);
        assertThat(response.getNewValue()).isEqualTo("FINANCE_OFFICER");
    }
}
