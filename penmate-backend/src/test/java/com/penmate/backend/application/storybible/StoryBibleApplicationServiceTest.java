package com.penmate.backend.application.storybible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.storybible.command.StoryBibleCommands;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleActorType;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleCategory;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleSemanticFamily;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryBibleApplicationServiceTest {

    private final StoryBibleRepository repository = mock(StoryBibleRepository.class);
    private final StoryBibleChangesetService changesetService = mock(StoryBibleChangesetService.class);
    private final BusinessIdGenerator idGenerator = mock(BusinessIdGenerator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StoryBibleSchemaValidator schemaValidator = new StoryBibleSchemaValidator(objectMapper);
    private final StoryBibleApplicationService service = new StoryBibleApplicationService(
            repository,
            changesetService,
            idGenerator,
            objectMapper,
            schemaValidator,
            new StoryBiblePatchValidator(objectMapper, schemaValidator),
            mock(StoryBibleEffectiveStateResolver.class)
    );

    @Test
    void should_create_node_and_append_one_aggregate_changeset() {
        StoryBible root = root();
        StoryBibleNodeType type = new StoryBibleNodeType();
        type.setTypeId(33L);
        type.setSemanticFamily(StoryBibleSemanticFamily.CHARACTER);
        type.setFieldSchemaJson("{}");
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findNodeType(10L, 33L)).thenReturn(type);
        when(repository.insertNode(any())).thenReturn(1);
        when(repository.findAliases(10L, 100L)).thenReturn(List.of());
        when(idGenerator.nextId()).thenReturn(100L);

        StoryBibleNode node = service.createNode(
                20L,
                new StoryBibleCommands.CreateNode(
                        33L, "Mira", "Pilot", "Body", "{\"age\":24}",
                        StoryBibleInclusionPolicy.AUTO_RETRIEVE, StoryBibleCanonStatus.CANON,
                        List.of(), List.of(), List.of()
                ),
                StoryBibleActorType.USER,
                9L,
                null
        );

        assertThat(node.getNodeId()).isEqualTo(100L);
        assertThat(node.getRevision()).isEqualTo(1L);
        assertThat(node.getCreatedBy()).isEqualTo(9L);
        verify(repository).insertNode(node);
        verify(changesetService).append(any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_record_agent_metadata_mutations_with_run_provenance() {
        StoryBible root = root();
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.insertCategory(any())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(101L);

        StoryBibleCategory category = service.createCategory(
                20L,
                new StoryBibleCommands.CreateCategory(null, "Continuity", 3),
                StoryBibleActorType.AGENT,
                9L,
                88L
        );

        assertThat(category.getCategoryId()).isEqualTo(101L);
        verify(changesetService).append(eq(root), eq(StoryBibleActorType.AGENT), eq(9L), eq(88L),
                eq("Created category"), any());
    }

    private StoryBible root() {
        StoryBible root = new StoryBible();
        root.setStoryBibleId(10L);
        root.setProjectId(20L);
        root.setContentRevision(4L);
        return root;
    }
}
