package org.newsanalyzer.repository.eval;

import org.newsanalyzer.model.eval.GenerationBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for GenerationBatch entities.
 * Spring Data auto-generates CRUD + pagination from JpaRepository.
 */
@Repository
public interface GenerationBatchRepository extends JpaRepository<GenerationBatch, UUID> {
}
