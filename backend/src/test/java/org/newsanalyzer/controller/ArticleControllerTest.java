package org.newsanalyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.CreateArticleRequest;
import org.newsanalyzer.exception.ResourceNotFoundException;
import org.newsanalyzer.model.ArticleStatus;
import org.newsanalyzer.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ArticleController using MockMvc.
 * Tests REST API endpoints with mocked service layer — mirrors
 * EntityControllerTest's structure exactly.
 */
@WebMvcTest(ArticleController.class)
class ArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ArticleService articleService;

    private CreateArticleRequest createRequest;
    private ArticleDTO articleDTO;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();

        createRequest = new CreateArticleRequest();
        createRequest.setSourceName("CNN");
        createRequest.setUrl("https://example.com/article");
        createRequest.setRawText("Full article text...");

        articleDTO = new ArticleDTO();
        articleDTO.setId(testId);
        articleDTO.setSourceName("CNN");
        articleDTO.setUrl("https://example.com/article");
        articleDTO.setRawText("Full article text...");
        articleDTO.setIngestedAt(LocalDateTime.now());
        articleDTO.setExtractionStatus(ArticleStatus.PENDING);
        articleDTO.setBiasDetectionStatus(ArticleStatus.PENDING);
    }

    @Test
    @WithMockUser
    void testCreateArticle() throws Exception {
        when(articleService.createArticle(any(CreateArticleRequest.class))).thenReturn(articleDTO);

        mockMvc.perform(post("/api/articles")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(testId.toString()))
            .andExpect(jsonPath("$.sourceName").value("CNN"))
            .andExpect(jsonPath("$.extractionStatus").value("PENDING"))
            .andExpect(jsonPath("$.biasDetectionStatus").value("PENDING"));

        verify(articleService).createArticle(any(CreateArticleRequest.class));
    }

    @Test
    @WithMockUser
    void testCreateArticleWithInvalidData() throws Exception {
        CreateArticleRequest invalidRequest = new CreateArticleRequest();
        // Missing required fields (sourceName, rawText)

        mockMvc.perform(post("/api/articles")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        verify(articleService, never()).createArticle(any());
    }

    @Test
    @WithMockUser
    void testCreateArticleWithSourceNameTooLong() throws Exception {
        // Article.sourceName is VARCHAR(255) at the DB level; this must be
        // rejected as a clean 400 here, not surface as a raw DB constraint
        // violation (409) after reaching the repository.
        CreateArticleRequest tooLong = new CreateArticleRequest();
        tooLong.setSourceName("x".repeat(256));
        tooLong.setRawText("Some text");

        mockMvc.perform(post("/api/articles")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tooLong)))
            .andExpect(status().isBadRequest());

        verify(articleService, never()).createArticle(any());
    }

    @Test
    @WithMockUser
    void testCreateArticleWithUrlTooLong() throws Exception {
        // Article.url is VARCHAR(1000) at the DB level — same rationale as above.
        CreateArticleRequest tooLong = new CreateArticleRequest();
        tooLong.setSourceName("CNN");
        tooLong.setUrl("https://example.com/" + "x".repeat(1000));
        tooLong.setRawText("Some text");

        mockMvc.perform(post("/api/articles")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tooLong)))
            .andExpect(status().isBadRequest());

        verify(articleService, never()).createArticle(any());
    }

    @Test
    @WithMockUser
    void testCreateArticleWithRawTextTooLong() throws Exception {
        CreateArticleRequest tooLong = new CreateArticleRequest();
        tooLong.setSourceName("CNN");
        tooLong.setRawText("x".repeat(100_001));

        mockMvc.perform(post("/api/articles")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tooLong)))
            .andExpect(status().isBadRequest());

        verify(articleService, never()).createArticle(any());
    }

    @Test
    @WithMockUser
    void testGetArticleById() throws Exception {
        when(articleService.getArticleById(testId)).thenReturn(articleDTO);

        mockMvc.perform(get("/api/articles/{id}", testId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testId.toString()))
            .andExpect(jsonPath("$.sourceName").value("CNN"));

        verify(articleService).getArticleById(testId);
    }

    @Test
    @WithMockUser
    void testGetArticleByIdNotFound() throws Exception {
        when(articleService.getArticleById(testId)).thenThrow(new ResourceNotFoundException("Article", testId));

        mockMvc.perform(get("/api/articles/{id}", testId))
            .andExpect(status().isNotFound());

        verify(articleService).getArticleById(testId);
    }

    @Test
    void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/articles/{id}", testId))
            .andExpect(status().isUnauthorized());

        verify(articleService, never()).getArticleById(any());
    }
}
