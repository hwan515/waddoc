package com.waddoc.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.waddoc", importOptions = ImportOption.DoNotIncludeTests.class)
class CoreAppServiceBoundaryArchTest {

    @ArchTest
    static final ArchRule core_app_should_not_depend_on_split_service_persistence_or_config =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.waddoc.domain.notification.entity..",
                            "com.waddoc.domain.notification.repository..",
                            "com.waddoc.domain.notification.config..",
                            "com.waddoc.domain.robot.config.."
                    );
}
