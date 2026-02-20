package tn.pi.remoteflowapplication.application.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.PendingValidationTaskDTO;
import tn.pi.remoteflowapplication.application.dto.WorkflowPendingTaskDTO;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.port.out.WorkflowOrchestrationPort;
import tn.pi.remoteflowapplication.application.service.ValidationInboxService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ValidationInboxServiceImpl implements ValidationInboxService {

    private static final String GROUP_MANAGER = "MANAGER";
    private static final String GROUP_HR = "HR";

    private final WorkflowOrchestrationPort workflowOrchestrationPort;
    private final TeleworkRequestRepository teleworkRequestRepository;
    private final UserRepository userRepository;

    public ValidationInboxServiceImpl(
            WorkflowOrchestrationPort workflowOrchestrationPort,
            TeleworkRequestRepository teleworkRequestRepository,
            UserRepository userRepository) {
        this.workflowOrchestrationPort = workflowOrchestrationPort;
        this.teleworkRequestRepository = teleworkRequestRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Page<PendingValidationTaskDTO> getPendingValidations(Authentication authentication, Pageable pageable) {
        Set<String> candidateGroups = resolveCandidateGroups(authentication);
        int fetchSize = Math.max((int) (pageable.getOffset() + pageable.getPageSize()), pageable.getPageSize());
        Pageable firstPageWithExpandedSize = PageRequest.of(0, fetchSize);

        List<WorkflowPendingTaskDTO> pendingTasks = new ArrayList<>();
        for (String group : candidateGroups) {
            pendingTasks.addAll(
                    workflowOrchestrationPort
                            .findPendingTasksByCandidateGroup(group, firstPageWithExpandedSize)
                            .getContent());
        }

        Map<String, PendingValidationTaskDTO> deduplicated = new LinkedHashMap<>();
        for (WorkflowPendingTaskDTO task : pendingTasks) {
            PendingValidationTaskDTO dto = toPendingValidationDto(task);
            deduplicated.put(dto.taskKey(), dto);
        }

        List<PendingValidationTaskDTO> ordered = deduplicated.values()
                .stream()
                .sorted(Comparator.comparing(PendingValidationTaskDTO::requestId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int fromIndex = (int) Math.min(pageable.getOffset(), ordered.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), ordered.size());
        List<PendingValidationTaskDTO> content = ordered.subList(fromIndex, toIndex);

        return new PageImpl<>(content, pageable, ordered.size());
    }

    private Set<String> resolveCandidateGroups(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("Utilisateur non authentifie");
        }

        Set<String> authorities = authentication.getAuthorities()
                .stream()
                .map(a -> a.getAuthority())
                .collect(java.util.stream.Collectors.toSet());

        boolean isAdmin = authorities.contains("ROLE_ADMIN");
        boolean isManager = authorities.contains("ROLE_MANAGER");
        boolean isHr = authorities.contains("ROLE_HR");

        if (isAdmin) {
            return Set.of(GROUP_MANAGER, GROUP_HR);
        }
        if (isManager && isHr) {
            return Set.of(GROUP_MANAGER, GROUP_HR);
        }
        if (isManager) {
            return Set.of(GROUP_MANAGER);
        }
        if (isHr) {
            return Set.of(GROUP_HR);
        }

        throw new BusinessException("Acces refuse: role manager/hr/admin requis");
    }

    private PendingValidationTaskDTO toPendingValidationDto(WorkflowPendingTaskDTO task) {
        TeleworkRequest request = resolveRequest(task);

        String employeeName = request == null
                ? "N/A"
                : userRepository.findByExternalId(request.getEmployeeId())
                        .map(user -> user.getFullName())
                        .orElse(request.getEmployeeId());

        return new PendingValidationTaskDTO(
                request == null ? task.requestId() : request.getId(),
                employeeName,
                request == null ? null : request.getStartDate(),
                request == null ? null : request.getEndDate(),
                request == null ? "SUBMITTED" : request.getStatus().name(),
                task.taskKey());
    }

    private TeleworkRequest resolveRequest(WorkflowPendingTaskDTO task) {
        if (task.requestId() != null) {
            return teleworkRequestRepository.findById(task.requestId()).orElse(null);
        }
        if (task.processInstanceId() != null && !task.processInstanceId().isBlank()) {
            return teleworkRequestRepository.findByProcessInstanceId(task.processInstanceId()).orElse(null);
        }
        return null;
    }
}

