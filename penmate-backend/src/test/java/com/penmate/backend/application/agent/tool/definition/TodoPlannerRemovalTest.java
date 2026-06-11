package com.penmate.backend.application.agent.tool.definition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TodoPlannerRemovalTest {

    @Test
    void should_not_register_todo_planner_as_agent_tool() {
        InMemoryAgentToolDefinitionSource source = new InMemoryAgentToolDefinitionSource(List.of(
                new TodoCrudToolDefinition()
        ));

        assertThat(source.listLlmSchemas())
                .extracting("toolCode")
                .doesNotContain("todo_planner");
        assertThatThrownBy(() -> Class.forName("com.penmate.backend.application.agent.tool.definition.TodoPlannerToolDefinition"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
