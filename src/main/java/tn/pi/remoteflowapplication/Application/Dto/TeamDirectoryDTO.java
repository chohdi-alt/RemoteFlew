package tn.pi.remoteflowapplication.application.dto;

public class TeamDirectoryDTO {
    private Long id;
    private String name;
    private String manager;
    private int membersCount;

    public TeamDirectoryDTO() {
    }

    public TeamDirectoryDTO(Long id, String name, String manager, int membersCount) {
        this.id = id;
        this.name = name;
        this.manager = manager;
        this.membersCount = membersCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManager() {
        return manager;
    }

    public void setManager(String manager) {
        this.manager = manager;
    }

    public int getMembersCount() {
        return membersCount;
    }

    public void setMembersCount(int membersCount) {
        this.membersCount = membersCount;
    }
}
