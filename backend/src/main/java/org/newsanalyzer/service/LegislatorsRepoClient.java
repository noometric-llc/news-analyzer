package org.newsanalyzer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.newsanalyzer.config.LegislatorsConfig;
import org.newsanalyzer.dto.LegislatorYamlRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client for fetching legislator data from the unitedstates/congress-legislators GitHub repository.
 *
 * Fetches YAML files from raw.githubusercontent.com and parses them using Jackson YAML.
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 * @see <a href="https://github.com/unitedstates/congress-legislators">GitHub Repository</a>
 */
@Service
public class LegislatorsRepoClient {

    private static final Logger log = LoggerFactory.getLogger(LegislatorsRepoClient.class);

    private final LegislatorsConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public LegislatorsRepoClient(LegislatorsConfig config, RestTemplateBuilder restTemplateBuilder, ObjectMapper jsonMapper) {
        this.config = config;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(config.getGithub().getTimeout()))
                .setReadTimeout(Duration.ofMillis(config.getGithub().getTimeout()))
                .build();
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(50 * 1024 * 1024); // 50 MB — legislators YAML files are ~10 MB
        this.yamlMapper = new ObjectMapper(YAMLFactory.builder().loaderOptions(loaderOptions).build());
        this.yamlMapper.findAndRegisterModules();
        this.jsonMapper = jsonMapper;
    }

    /**
     * Fetch current legislators from the GitHub repository.
     *
     * @return List of legislator records
     */
    public List<LegislatorYamlRecord> fetchCurrentLegislators() {
        String url = config.getGithub().getBaseUrl() + "/" + config.getGithub().getCurrentFile();
        return fetchYamlFile(url, "current legislators");
    }

    /**
     * Fetch historical legislators from the GitHub repository.
     *
     * @return List of legislator records
     */
    public List<LegislatorYamlRecord> fetchHistoricalLegislators() {
        String url = config.getGithub().getBaseUrl() + "/" + config.getGithub().getHistoricalFile();
        return fetchYamlFile(url, "historical legislators");
    }

    /**
     * Fetch the latest commit SHA from the main branch.
     *
     * @return Optional containing the commit SHA, or empty if request fails
     */
    public Optional<String> fetchLatestCommitSha() {
        String url = config.getGithub().getApiUrl() + "/commits/main";
        log.debug("Fetching latest commit SHA from: {}", url);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = jsonMapper.readTree(response.getBody());
                String sha = root.path("sha").asText(null);
                if (sha != null) {
                    log.info("Latest commit SHA: {}", sha);
                    return Optional.of(sha);
                }
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch latest commit SHA: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error parsing commit response: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Fetch and parse a YAML file from the given URL.
     *
     * @param url The URL to fetch
     * @param description Description for logging
     * @return List of legislator records
     */
    private List<LegislatorYamlRecord> fetchYamlFile(String url, String description) {
        log.info("Fetching {} from: {}", description, url);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to fetch {}: HTTP {}", description, response.getStatusCode());
                return Collections.emptyList();
            }

            String yaml = response.getBody();
            if (yaml == null || yaml.isEmpty()) {
                log.warn("Empty response when fetching {}", description);
                return Collections.emptyList();
            }

            List<LegislatorYamlRecord> records = yamlMapper.readValue(yaml, new TypeReference<List<LegislatorYamlRecord>>() {});
            log.info("Successfully parsed {} {} records", records.size(), description);
            return records;

        } catch (RestClientException e) {
            log.error("HTTP error fetching {}: {}", description, e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error parsing {} YAML: {}", description, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Fetch current committee membership data from the GitHub repository.
     *
     * The YAML file maps Thomas committee IDs to lists of member records.
     * Example: SSJU: [{name: ..., bioguide: G000359, title: Chair}, ...]
     *
     * @return Map of Thomas committee ID to list of member records
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<Map<String, Object>>> fetchCommitteeMembershipCurrent() {
        String url = config.getGithub().getBaseUrl() + "/committee-membership-current.yaml";
        log.info("Fetching committee membership data from: {}", url);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to fetch committee membership data: HTTP {}", response.getStatusCode());
                return Collections.emptyMap();
            }

            String yaml = response.getBody();
            if (yaml == null || yaml.isEmpty()) {
                log.warn("Empty response when fetching committee membership data");
                return Collections.emptyMap();
            }

            Map<String, List<Map<String, Object>>> data = yamlMapper.readValue(
                    yaml, new TypeReference<LinkedHashMap<String, List<Map<String, Object>>>>() {});
            log.info("Successfully parsed committee membership data: {} committees", data.size());
            return data;

        } catch (RestClientException e) {
            log.error("HTTP error fetching committee membership data: {}", e.getMessage());
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("Error parsing committee membership YAML: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Check if the GitHub API is accessible.
     *
     * @return true if the API responds successfully
     */
    public boolean isGitHubAccessible() {
        try {
            String url = config.getGithub().getApiUrl();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("GitHub API not accessible: {}", e.getMessage());
            return false;
        }
    }
}
