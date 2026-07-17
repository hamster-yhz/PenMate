package com.penmate.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.penmate.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class AgentArchitectureDependencyTest {

    @ArchTest
    static final ArchRule agent_usecase_should_not_depend_on_llm_provider_implementations =
            noClasses()
                    .that().resideInAPackage("..application.agent.usecase..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure.llm.langchain4j.provider..");

    @ArchTest
    static final ArchRule domain_agent_service_should_not_depend_on_spring =
            noClasses()
                    .that().resideInAPackage("..domain.agent.service..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.springframework..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_agent_should_not_depend_on_legacy_agent_json_helpers =
            noClasses()
                    .that().resideInAPackage("..application.agent..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..application.agent.json..");

    @ArchTest
    static final ArchRule llm_provider_should_not_depend_on_application_agent_json_helpers =
            noClasses()
                    .that().resideInAPackage("..infrastructure.llm.langchain4j.provider..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..application.agent.json..");

    @ArchTest
    static final ArchRule approval_application_should_not_depend_on_legacy_agent_root_or_loop_packages =
            noClasses()
                    .that().resideInAPackage("..application.approval..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..application.agent.loop..",
                            "..application.agent.AgentTaskStateMachine"
                    );

    @ArchTest
    static final ArchRule application_agent_root_package_should_only_keep_whitelisted_classes =
            noClasses()
                    .that().resideInAPackage("..application.agent")
                    .should(new ArchCondition<>("be migrated into usecase/orchestration/tool/llm subpackages") {
                        @Override
                        public void check(JavaClass item, ConditionEvents events) {
                            boolean allowed = item.getSimpleName().equals("AgentDomainConfig")
                                    || item.getSimpleName().equals("AgentTaskStateMachine")
                                    || item.getSimpleName().equals("AgentModelRoutingService");
                            if (!allowed) {
                                String message = item.getName() + " should not stay in application.agent root package";
                                events.add(SimpleConditionEvent.violated(item, message));
                            }
                        }
                    });
}
