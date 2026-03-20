package tn.pi.remoteflowapplication.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.pi.remoteflowapplication.application.dto.TeamDirectoryDTO;
import tn.pi.remoteflowapplication.application.dto.UserDirectoryDTO;
import tn.pi.remoteflowapplication.application.service.DirectoryService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/directory")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDirectoryController {

    private final DirectoryService directoryService;

    public AdminDirectoryController(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping("/users")
    public List<UserDirectoryDTO> getAllUsers() {
        return directoryService.getAllUsers();
    }

    @GetMapping("/teams")
    public List<TeamDirectoryDTO> getAllTeams() {
        return directoryService.getAllTeams();
    }

    @GetMapping("/managers")
    public List<UserDirectoryDTO> getManagers() {
        return directoryService.getManagers();
    }

    @GetMapping("/hr")
    public List<UserDirectoryDTO> getHr() {
        return directoryService.getHr();
    }

    @GetMapping("/team/{teamId}/members")
    public List<UserDirectoryDTO> getTeamMembers(@PathVariable Long teamId) {
        return directoryService.getTeamMembers(teamId);
    }
}
