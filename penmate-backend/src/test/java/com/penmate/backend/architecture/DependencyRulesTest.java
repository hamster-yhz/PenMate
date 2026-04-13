package com.penmate.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.penmate.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class DependencyRulesTest {

    @ArchTest
    static final ArchRule application_should_not_depend_on_infrastructure_persistence =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule application_should_not_depend_on_interface_dto =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..interfaces.api..dto..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_application_interfaces_or_infrastructure =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..application..", "..interfaces..", "..infrastructure..");
}

