package com.penmate.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.penmate.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class DependencyRulesTest {

    @ArchTest
    static final ArchRule application_should_not_depend_on_infrastructure_or_interfaces =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure..", "..interfaces..");

    @ArchTest
    static final ArchRule application_should_not_depend_on_redis_implementations =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework.data.redis..");

    @ArchTest
    static final ArchRule application_should_not_depend_on_http_transport =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework.http..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule application_should_not_own_framework_triggers =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.scheduling..",
                            "org.springframework.transaction.event..",
                            "org.springframework.context.event..");

    @ArchTest
    static final ArchRule application_should_publish_events_through_ports =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.context.ApplicationEventPublisher");

    @ArchTest
    static final ArchRule application_should_use_serialization_ports =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.fasterxml.jackson..", "cn.hutool..");

    @ArchTest
    static final ArchRule rag_application_should_use_serialization_and_configuration_ports =
            noClasses()
                    .that().resideInAPackage("..application.rag..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule rag_application_should_not_read_external_configuration =
            noClasses()
                    .that().resideInAPackage("..application.rag..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Value");

    @ArchTest
    static final ArchRule story_bible_application_should_use_serialization_ports =
            noClasses()
                    .that().resideInAPackage("..application.storybible..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.fasterxml.jackson..",
                            "com.networknt.schema..",
                            "com.github.fge.jsonpatch..");

    @ArchTest
    static final ArchRule agent_context_application_should_use_serialization_ports =
            noClasses()
                    .that().resideInAPackage("..application.agent.context..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_application_interfaces_or_infrastructure =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..application..", "..interfaces..", "..infrastructure..");

    @ArchTest
    static final ArchRule interfaces_should_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("..interfaces..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule infrastructure_should_not_depend_on_interfaces =
            noClasses()
                    .that().resideInAPackage("..infrastructure..")
                    .should().dependOnClassesThat().resideInAPackage("..interfaces..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_framework_or_serialization_packages =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "com.fasterxml.jackson..",
                            "com.baomidou..",
                            "jakarta.persistence..");
}

