package com.waddoc.bff.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.waddoc.bff", importOptions = ImportOption.DoNotIncludeTests.class)
class EdgeBffServiceBoundaryArchTest {

    @ArchTest
    static final ArchRule edge_bff_should_not_depend_on_service_persistence_or_service_local_config =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.waddoc.domain..entity..",
                            "com.waddoc.domain..repository..",
                            "com.waddoc.domain.notification.config..",
                            "com.waddoc.domain.robot.config.."
                    );
}
