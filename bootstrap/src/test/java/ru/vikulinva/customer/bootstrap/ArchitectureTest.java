package ru.vikulinva.customer.bootstrap;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "ru.vikulinva.customer")
public class ArchitectureTest {

    @ArchTest
    static final ArchRule core_must_not_depend_on_spring_web = noClasses()
            .that().resideInAPackage("..customer.core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..",
                    "org.springframework.security..",
                    "org.springframework.boot..",
                    "org.springframework.data..",
                    "org.springframework.kafka..",
                    "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule core_must_not_depend_on_jooq = noClasses()
            .that().resideInAPackage("..customer.core..")
            .should().dependOnClassesThat().resideInAnyPackage("org.jooq..");

    @ArchTest
    static final ArchRule core_must_not_depend_on_jackson = noClasses()
            .that().resideInAPackage("..customer.core..")
            .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml..");

    @ArchTest
    static final ArchRule core_must_not_depend_on_persistence = noClasses()
            .that().resideInAPackage("..customer.core..")
            .should().dependOnClassesThat().resideInAnyPackage("..customer.persistence..");

    @ArchTest
    static final ArchRule in_adapter_must_not_depend_on_out_adapter = noClasses()
            .that().resideInAPackage("..customer.adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..customer.persistence..", "..customer.adapter.out..")
            .allowEmptyShould(true);
}
