package tn.pi.remoteflowapplication.application.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.PendingValidationTaskDTO;
import tn.pi.remoteflowapplication.application.port.out.TeamRepository;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.service.WorkflowTaskService;
import tn.pi.remoteflowapplication.domain.entity.TaskEntity;
import tn.pi.remoteflowapplication.application.service.ValidationInboxService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ValidationInboxServiceImpl implements ValidationInboxService {

    private static final String GROUP_MANAGER = "MANAGER";
    private static final String GROUP_HR = "HR";

    private final WorkflowTaskService workflowTaskService;
    private final TeleworkRequestRepository teleworkRequestRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    
    public ValidationInboxServiceImpl(
            WorkflowTaskService workflowTaskService,
            TeleworkRequestRepository teleworkRequestRepository,
            UserRepository userRepository,
            TeamRepository teamRepository) {
        this.workflowTaskService = workflowTaskService;
        this.teleworkRequestRepository = teleworkRequestRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    @Override
    public Page<PendingValidationTaskDTO> getPendingValidations(Authentication authentication, Pageable pageable) {
        String currentUsername = authentication.getName();
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());

        boolean isAdmin = authorities.contains("ROLE_ADMIN");
        boolean isHr = authorities.contains("ROLE_HR");
        boolean isManager = authorities.contains("ROLE_MANAGER");

        Set<String> candidateGroups = resolveCandidateGroups(authorities, isAdmin, isHr, isManager);

        List<TaskEntity> pendingTasks = new ArrayList<>();
        if (candidateGroups.contains(GROUP_MANAGER)) {
            pendingTasks.addAll(workflowTaskService.getPendingTasksForManager());
        }
        if (candidateGroups.contains(GROUP_HR)) {
            pendingTasks.addAll(workflowTaskService.getPendingTasksForHr());
        }

        List<Long> managedTeamIds = isManager && !isAdmin && !isHr
                ? teamRepository.findManagedTeamIdsByUsername(currentUsername)
                : Collections.emptyList();

        Map<String, PendingValidationTaskDTO> deduplicated = new LinkedHashMap<>();
        for (TaskEntity task : pendingTasks) {
            TeleworkRequest request = resolveRequest(task);
            
            // Apply filtering for strict managers
            if (isManager && !isAdmin && !isHr) {
                if (request != null) {
                    Long rTeamId = request.getTeamId();
                    if (rTeamId == null && request.getEquipe() != null) {
                        rTeamId = request.getEquipe().getId();
                    }
                    
                    if (rTeamId == null || !managedTeamIds.contains(rTeamId)) {
                        continue; 
                    }
                }
                // If request is null, we keep it in the list (will show as N/A) to avoid hiding tasks with missing metadata
            }

            PendingValidationTaskDTO dto = mapToPendingValidationDto(task, request);
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

    private Set<String> resolveCandidateGroups(Set<String> authorities, boolean isAdmin, boolean isHr, boolean isManager) {
        if (isAdmin || (isManager && isHr)) {
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

    private PendingValidationTaskDTO mapToPendingValidationDto(TaskEntity task, TeleworkRequest request) {
        String employeeName = request == null
                ? "N/A"
                : userRepository.findByUsername(request.getEmployeeId())
                        .or(() -> userRepository.findByKeycloakId(request.getEmployeeId()))
                        .map(user -> user.getFullName())
                        .orElse(request.getEmployeeId());

        return new PendingValidationTaskDTO(
                request == null ? task.getRequestId() : request.getId(),
                employeeName,
                request == null ? null : request.getStartDate(),
                request == null ? null : request.getEndDate(),
                request == null ? "SUBMITTED" : request.getStatus().name(),
                String.valueOf(task.getId()),
                request == null ? null : request.getJustificationReason(),
                request == null || request.getAlfrescoNodeId() == null ? null : request.getAlfrescoNodeId().replace("workspace://SpacesStore/", ""),
                request == null ? null : request.getManagerComment(),
                request == null ? null : request.getHrComment()
        );
    }

    private TeleworkRequest resolveRequest(TaskEntity task) {
        if (task.getRequestId() != null) {
            return teleworkRequestRepository.findById(task.getRequestId()).orElse(null);
        }
        return null;
    }
}
