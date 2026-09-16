package com.homechores;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.homechores.i18n.LocaleInitListener;
import com.homechores.maintenance.MaintenanceRunner;
import com.homechores.service.HomeState;
import com.homechores.service.WebPushSender;
import com.homechores.ui.SessionContext;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * The shape of the codebase, as executable rules (ArchUnit). Each rule below pins a convention
 * the code already follows — the point is that the next change cannot quietly break it.
 *
 * <p>The layering: {@code ui} (Vaadin views, panels, dialogs) talks to {@code service}, which
 * talks to {@code domain} (JPA entities, repositories, value helpers). {@code i18n} is a leaf
 * used by {@code ui} and {@code service}; {@code maintenance} is the operator CLI, which nothing
 * depends on. {@code domain} depends on nothing in-house and nothing from Vaadin, so the data
 * model can be reasoned about — and backed up, restored, and migrated — without a UI in the
 * picture. Services never touch Vaadin either, with two named exceptions that are the whole
 * point of those classes: {@link HomeState} wraps a Flow shared signal, and
 * {@link WebPushSender} wraps Flow's Web Push client.
 *
 * <p>Runs against the compiled main classes only ({@code DO_NOT_INCLUDE_TESTS}): tests are
 * allowed to reach into repositories and field-inject freely.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.homechores");
    }

    // ---- Layers ---------------------------------------------------------------

    @Test
    void layersOnlyPointDownwards() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("UI").definedBy("..homechores.ui..")
                .layer("Service").definedBy("..homechores.service..")
                .layer("Domain").definedBy("..homechores.domain..")
                .layer("I18n").definedBy("..homechores.i18n..")
                .layer("Maintenance").definedBy("..homechores.maintenance..")
                .whereLayer("UI").mayNotBeAccessedByAnyLayer()
                .whereLayer("Maintenance").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("UI", "Maintenance")
                .whereLayer("I18n").mayOnlyBeAccessedByLayers("UI", "Service")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("UI", "Service", "Maintenance", "I18n")
                .check(classes);
    }

    @Test
    void packagesAreFreeOfCycles() {
        slices().matching("com.homechores.(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    void domainDependsOnNothingInHouse_andNothingFromVaadin() {
        noClasses().that().resideInAPackage("..homechores.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..homechores.service..", "..homechores.ui..", "..homechores.i18n..",
                        "..homechores.maintenance..", "com.vaadin..")
                .because("the data model must stand on its own (backup, restore, migration, tests)")
                .check(classes);
    }

    @Test
    void servicesNeverDependOnTheUi() {
        noClasses().that().resideInAPackage("..homechores.service..")
                .should().dependOnClassesThat().resideInAPackage("..homechores.ui..")
                .check(classes);
    }

    @Test
    void servicesNeverDependOnVaadin_exceptTheTwoFlowWrappers() {
        noClasses().that().resideInAPackage("..homechores.service..")
                .and().doNotBelongToAnyOf(HomeState.class, WebPushSender.class)
                .should().dependOnClassesThat().resideInAPackage("com.vaadin..")
                .because("HomeState (shared signals) and WebPushSender (flow-webpush) are the only "
                        + "places a service may know about Flow")
                .check(classes);
    }

    @Test
    void nothingDependsOnTheMaintenanceCli() {
        classes().that().resideInAPackage("..homechores.maintenance..")
                .should().onlyHaveDependentClassesThat().resideInAPackage("..homechores.maintenance..")
                .check(classes);
    }

    // ---- Persistence ------------------------------------------------------------

    @Test
    void repositoriesAreDomainInterfaces_usedOnlyByServicesAndTheCli() {
        classes().that().haveSimpleNameEndingWith("Repository")
                .should().beInterfaces()
                .andShould().resideInAPackage("..homechores.domain..")
                .andShould().beAssignableTo(JpaRepository.class)
                .andShould().onlyHaveDependentClassesThat().resideInAnyPackage(
                        "..homechores.domain..", "..homechores.service..", "..homechores.maintenance..")
                .because("the UI goes through services; a repository in a panel bypasses the "
                        + "clipping, the home-membership checks and the HomeState bump")
                .check(classes);
    }

    @Test
    void repositoriesUseDerivedQueriesOnly() {
        noMethods().should().beAnnotatedWith(Query.class)
                .orShould().beAnnotatedWith(Modifying.class)
                .because("derived query names are the repository convention here (see "
                        + "ChoreTaskRepository's note on null arguments)")
                .check(classes);
    }

    @Test
    void entitiesLiveInDomain_withAProtectedNoArgConstructor() {
        classes().that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage("..homechores.domain..")
                .andShould(haveAProtectedNoArgConstructor())
                .check(classes);
    }

    @Test
    void transactionsAreSpringsNotJakartas() {
        noClasses().should().dependOnClassesThat().resideInAPackage("jakarta.transaction..")
                .because("mixing the two @Transactional annotations silently disables one of them")
                .check(classes);
    }

    // ---- Spring & Vaadin conventions -----------------------------------------

    @Test
    void wiringIsByConstructor_neverByField() {
        noFields().should().beAnnotatedWith(Autowired.class).check(classes);
    }

    @Test
    void servicesAndRoutesLiveWhereTheirNamesSay() {
        classes().that().areAnnotatedWith(Service.class)
                .should().resideInAPackage("..homechores.service..").check(classes);
        classes().that().areAnnotatedWith(Route.class)
                .should().resideInAPackage("..homechores.ui..").check(classes);
    }

    @Test
    void componentsOutsideServiceAreVaadinInitListeners() {
        classes().that().areAnnotatedWith(Component.class)
                .and().resideOutsideOfPackages("..homechores.service..", "..homechores.maintenance..",
                        "..homechores.i18n..")
                .should().resideInAPackage("..homechores.ui..")
                .andShould().implement(VaadinServiceInitListener.class)
                .because("a ui @Component is a VaadinServiceInitListener hook; anything else "
                        + "with a lifecycle belongs in service")
                .check(classes);
    }

    @Test
    void theVaadinSessionAndCurrentUiAreTouchedOnlyByTheUiPackage() {
        noClasses().that().resideOutsideOfPackage("..homechores.ui..")
                .and().doNotBelongToAnyOf(LocaleInitListener.class)
                .should().accessClassesThat().belongToAnyOf(UI.class, VaadinSession.class)
                .because("services take the member, home and zone as parameters — that is what "
                        + "keeps them testable and what the scheduler threads rely on; the one "
                        + "exception is the session-init hook that seeds the session locale")
                .check(classes);
    }

    @Test
    void uiHelpersArePackagePrivate() {
        classes().that().resideInAPackage("..homechores.ui..")
                .and().areTopLevelClasses()
                .and().areNotAnnotatedWith(Route.class)
                .and().doNotImplement(VaadinServiceInitListener.class)
                .and().doNotBelongToAnyOf(SessionContext.class)
                .should().notBePublic()
                .because("panels, dialogs and helpers are an implementation detail of the views; "
                        + "SessionContext is the one deliberate public facade")
                .check(classes);
    }

    // ---- General hygiene ---------------------------------------------------------

    @Test
    void noStandardStreams_exceptTheCliWhoseOutputTheyAre() {
        noClasses().that().doNotBelongToAnyOf(MaintenanceRunner.class)
                .should(ACCESS_STANDARD_STREAMS)
                .because("MaintenanceRunner's stdout JSON envelope is its documented contract; "
                        + "everything else logs through SLF4J")
                .check(classes);
    }

    @Test
    void noJavaUtilLogging() {
        noClasses().should().dependOnClassesThat().resideInAPackage("java.util.logging..")
                .check(classes);
    }

    // ---- helpers ------------------------------------------------------------------

    /** JPA needs it, and {@code protected} keeps it out of the way of application code. */
    private static ArchCondition<JavaClass> haveAProtectedNoArgConstructor() {
        return new ArchCondition<>("have a protected no-arg constructor") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean ok = item.getConstructors().stream()
                        .filter(c -> c.getRawParameterTypes().isEmpty())
                        .map(JavaConstructor::getModifiers)
                        .anyMatch(m -> m.contains(JavaModifier.PROTECTED));
                events.add(new SimpleConditionEvent(item, ok,
                        item.getName() + (ok ? " has" : " lacks") + " a protected no-arg constructor"));
            }
        };
    }
}
