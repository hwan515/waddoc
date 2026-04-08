package com.waddoc.robot.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.waddoc", importOptions = ImportOption.DoNotIncludeTests.class)
class RobotGatewayBoundaryArchTest {

    @ArchTest
    static final ArchRule robot_gateway_should_not_depend_on_other_service_persistence_or_config =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.waddoc.domain.admin.entity..",
                            "com.waddoc.domain.admin.repository..",
                            "com.waddoc.domain.audit.entity..",
                            "com.waddoc.domain.audit.repository..",
                            "com.waddoc.domain.auth.entity..",
                            "com.waddoc.domain.auth.repository..",
                            "com.waddoc.domain.booking.entity..",
                            "com.waddoc.domain.booking.repository..",
                            "com.waddoc.domain.carecase.entity..",
                            "com.waddoc.domain.carecase.repository..",
                            "com.waddoc.domain.consultation.entity..",
                            "com.waddoc.domain.consultation.repository..",
                            "com.waddoc.domain.dispatch.entity..",
                            "com.waddoc.domain.dispatch.repository..",
                            "com.waddoc.domain.doctor.entity..",
                            "com.waddoc.domain.doctor.repository..",
                            "com.waddoc.domain.guardian.entity..",
                            "com.waddoc.domain.guardian.repository..",
                            "com.waddoc.domain.intake.entity..",
                            "com.waddoc.domain.intake.repository..",
                            "com.waddoc.domain.mission.entity..",
                            "com.waddoc.domain.mission.repository..",
                            "com.waddoc.domain.patient.entity..",
                            "com.waddoc.domain.patient.repository..",
                            "com.waddoc.domain.user.entity..",
                            "com.waddoc.domain.user.repository..",
                            "com.waddoc.domain.vehicle.entity..",
                            "com.waddoc.domain.vehicle.repository..",
                            "com.waddoc.domain.vital.entity..",
                            "com.waddoc.domain.vital.repository..",
                            "com.waddoc.domain.notification.entity..",
                            "com.waddoc.domain.notification.repository..",
                            "com.waddoc.domain.notification.config.."
                    );
}
