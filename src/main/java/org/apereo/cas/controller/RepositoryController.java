package org.apereo.cas.controller;

import org.apereo.cas.CasLabels;
import org.apereo.cas.MonitoredRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;


@RestController
@Slf4j
public class RepositoryController {

    @Autowired
    private MonitoredRepository repository;

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured({"ROLE_ADMIN"})
    public Map<String, String> home() {
        var map = new LinkedHashMap<String, String>();
        try {
            map.put("name", repository.getOrganization() + '/' + repository.getName());
            map.put("repository", repository.getGitHubProperties().getRepository().getUrl());
            map.put("version", repository.getCurrentVersionInMaster().toString());
        } catch (final Exception e) {
            log.error(e.getMessage(), e);
        }
        return map;
    }
    
    @PostMapping(value = "/repo/pulls/{prNumber}/labels/automerge", produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured({"ROLE_ADMIN"})
    public ResponseEntity automerge(@PathVariable final String prNumber) {
        val pullRequest = repository.getPullRequest(prNumber);
        if (pullRequest == null) {
            return ResponseEntity.notFound().build();
        }

        if (pullRequest.isLocked()) {
            return ResponseEntity.status(HttpStatus.LOCKED).build();
        }

        if (!pullRequest.isLabeledAs(CasLabels.LABEL_AUTO_MERGE)) {
            repository.labelPullRequestAs(pullRequest, CasLabels.LABEL_AUTO_MERGE);
        }
        return ResponseEntity.ok().build();
    }
}
