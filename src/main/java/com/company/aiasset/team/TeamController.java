package com.company.aiasset.team;

import com.company.aiasset.security.CurrentUser;
import com.company.aiasset.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamRepository teamRepository;

    public TeamController(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @GetMapping
    public List<TeamDto> list(CurrentUser currentUser) {
        return teamRepository.findAll().stream()
                .map(TeamDto::from)
                .toList();
    }

    @PostMapping
    public TeamDto create(@RequestBody CreateTeamRequest req, CurrentUser currentUser) {
        if (!currentUser.hasRole(User.Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可创建团队");
        }

        if (teamRepository.existsByName(req.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "团队名称已存在");
        }

        Team team = new Team(req.name());
        return TeamDto.from(teamRepository.save(team));
    }

    @PutMapping("/{id}")
    public TeamDto update(@PathVariable UUID id, @RequestBody UpdateTeamRequest req, CurrentUser currentUser) {
        if (!currentUser.hasRole(User.Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可修改团队");
        }

        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!team.getName().equals(req.name()) && teamRepository.existsByName(req.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "团队名称已存在");
        }

        team.setName(req.name());
        return TeamDto.from(teamRepository.save(team));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, CurrentUser currentUser) {
        if (!currentUser.hasRole(User.Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可删除团队");
        }

        if (!teamRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        teamRepository.deleteById(id);
    }

    public record CreateTeamRequest(String name) {}
    public record UpdateTeamRequest(String name) {}
}
