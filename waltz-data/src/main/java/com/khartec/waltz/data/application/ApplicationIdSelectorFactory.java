/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.khartec.waltz.data.application;

import com.khartec.waltz.data.data_type.DataTypeIdSelectorFactory;
import com.khartec.waltz.data.measurable.MeasurableIdSelectorFactory;
import com.khartec.waltz.data.orgunit.OrganisationalUnitIdSelectorFactory;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.EntityReference;
import com.khartec.waltz.model.ImmutableIdSelectionOptions;
import com.khartec.waltz.model.application.ApplicationIdSelectionOptions;
import com.khartec.waltz.schema.tables.*;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Function;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.common.Checks.checkTrue;
import static com.khartec.waltz.data.SelectorUtilities.ensureScopeIsExact;
import static com.khartec.waltz.data.SelectorUtilities.mkApplicationConditions;
import static com.khartec.waltz.data.logical_flow.LogicalFlowDao.NOT_REMOVED;
import static com.khartec.waltz.model.EntityLifecycleStatus.REMOVED;
import static com.khartec.waltz.model.HierarchyQueryScope.EXACT;
import static com.khartec.waltz.schema.Tables.*;
import static com.khartec.waltz.schema.tables.Application.APPLICATION;
import static com.khartec.waltz.schema.tables.ApplicationGroupEntry.APPLICATION_GROUP_ENTRY;
import static com.khartec.waltz.schema.tables.EntityRelationship.ENTITY_RELATIONSHIP;
import static com.khartec.waltz.schema.tables.FlowDiagramEntity.FLOW_DIAGRAM_ENTITY;
import static com.khartec.waltz.schema.tables.Involvement.INVOLVEMENT;
import static com.khartec.waltz.schema.tables.LogicalFlow.LOGICAL_FLOW;
import static com.khartec.waltz.schema.tables.LogicalFlowDecorator.LOGICAL_FLOW_DECORATOR;
import static com.khartec.waltz.schema.tables.MeasurableRating.MEASURABLE_RATING;
import static com.khartec.waltz.schema.tables.Person.PERSON;
import static com.khartec.waltz.schema.tables.PersonHierarchy.PERSON_HIERARCHY;
import static com.khartec.waltz.schema.tables.PhysicalFlow.PHYSICAL_FLOW;

@Service
public class ApplicationIdSelectorFactory implements Function<ApplicationIdSelectionOptions, Select<Record1<Long>>> {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationIdSelectorFactory.class);

    private final DSLContext dsl;
    private final DataTypeIdSelectorFactory dataTypeIdSelectorFactory;
    private final MeasurableIdSelectorFactory measurableIdSelectorFactory;
    private final OrganisationalUnitIdSelectorFactory orgUnitIdSelectorFactory;

    private final ApplicationGroupEntry appGroupAppEntry = APPLICATION_GROUP_ENTRY.as("agae");
    private final ApplicationGroupOuEntry appGroupOuEntry = APPLICATION_GROUP_OU_ENTRY.as("agoe");
    private final FlowDiagramEntity flowDiagram = FLOW_DIAGRAM_ENTITY.as("fd");
    private final Involvement involvement = INVOLVEMENT.as("inv");
    private final LogicalFlow logicalFlow = LOGICAL_FLOW.as("lf");
    private final MeasurableRating measurableRating = MEASURABLE_RATING.as("mr");
    private final Person person = PERSON.as("p");
    private final PersonHierarchy personHierarchy = PERSON_HIERARCHY.as("ph");


    @Autowired
    public ApplicationIdSelectorFactory(DSLContext dsl,
                                        DataTypeIdSelectorFactory dataTypeIdSelectorFactory,
                                        MeasurableIdSelectorFactory measurableIdSelectorFactory, 
                                        OrganisationalUnitIdSelectorFactory orgUnitIdSelectorFactory) {
        checkNotNull(dsl, "dsl cannot be null");
        checkNotNull(dataTypeIdSelectorFactory, "dataTypeIdSelectorFactory cannot be null");
        checkNotNull(measurableIdSelectorFactory, "measurableIdSelectorFactory cannot be null");
        checkNotNull(orgUnitIdSelectorFactory, "orgUnitIdSelectorFactory cannot be null");

        this.dsl = dsl;
        this.dataTypeIdSelectorFactory = dataTypeIdSelectorFactory;
        this.measurableIdSelectorFactory = measurableIdSelectorFactory;
        this.orgUnitIdSelectorFactory = orgUnitIdSelectorFactory;
    }


    public Select<Record1<Long>> apply(ApplicationIdSelectionOptions options) {
        checkNotNull(options, "options cannot be null");
        EntityReference ref = options.entityReference();
        switch (ref.kind()) {
            case ACTOR:
                return mkForActor(options);
            case APP_GROUP:
                return mkForAppGroup(options);
            case APPLICATION:
                return mkForApplication(options);
            case CHANGE_INITIATIVE:
                return mkForEntityRelationship(options);
            case DATA_TYPE:
                return mkForDataType(options);
            case FLOW_DIAGRAM:
                return mkForFlowDiagram(options);
            case LICENCE:
                return mkForEntityRelationship(options);
            case MEASURABLE:
                return mkForMeasurable(options);
            case SCENARIO:
                return mkForScenario(options);
            case SERVER:
                return mkForServer(options);
            case ORG_UNIT:
                return mkForOrgUnit(options);
            case PERSON:
                return mkForPerson(options);
            case SOFTWARE:
                return mkForSoftwarePackage(options);
            default:
                throw new IllegalArgumentException("Cannot create selector for entity kind: " + ref.kind());
        }
    }


    private Select<Record1<Long>> mkForSoftwarePackage(ApplicationIdSelectionOptions options) {
        ensureScopeIsExact(options);

        Condition applicationConditions = mkApplicationConditions(options);
        return dsl
                .selectDistinct(SOFTWARE_USAGE.APPLICATION_ID)
                .from(SOFTWARE_USAGE)
                .innerJoin(APPLICATION)
                .on(APPLICATION.ID.eq(SOFTWARE_USAGE.APPLICATION_ID))
                .where(SOFTWARE_USAGE.SOFTWARE_PACKAGE_ID.eq(options.entityReference().id()))
                .and(applicationConditions);
    }


    private Select<Record1<Long>> mkForScenario(ApplicationIdSelectionOptions options) {
        ensureScopeIsExact(options);

        Condition applicationConditions = mkApplicationConditions(options);
        return DSL
                .selectDistinct(SCENARIO_RATING_ITEM.DOMAIN_ITEM_ID)
                .from(SCENARIO_RATING_ITEM)
                .innerJoin(APPLICATION).on(APPLICATION.ID.eq(SCENARIO_RATING_ITEM.DOMAIN_ITEM_ID))
                .and(SCENARIO_RATING_ITEM.DOMAIN_ITEM_KIND.eq(EntityKind.APPLICATION.name()))
                .where(SCENARIO_RATING_ITEM.SCENARIO_ID.eq(options.entityReference().id()))
                .and(applicationConditions);
    }


    private Select<Record1<Long>> mkForServer(ApplicationIdSelectionOptions options) {
        ensureScopeIsExact(options);

        Condition applicationConditions = mkApplicationConditions(options);

        return DSL.selectDistinct(SERVER_USAGE.ENTITY_ID)
                .from(SERVER_USAGE)
                .innerJoin(APPLICATION).on(APPLICATION.ID.eq(SERVER_USAGE.ENTITY_ID))
                .where(SERVER_USAGE.SERVER_ID.eq(options.entityReference().id()))
                .and(SERVER_USAGE.ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                .and(applicationConditions);
    }


    private Select<Record1<Long>> mkForActor(ApplicationIdSelectionOptions options) {
        ensureScopeIsExact(options);
        long actorId = options.entityReference().id();

        Condition applicationConditions = mkApplicationConditions(options);

        Select<Record1<Long>> sourceAppIds = DSL.select(logicalFlow.SOURCE_ENTITY_ID)
                .from(logicalFlow)
                .innerJoin(APPLICATION).on(APPLICATION.ID.eq(logicalFlow.SOURCE_ENTITY_ID))
                .where(logicalFlow.TARGET_ENTITY_ID.eq(actorId)
                        .and(logicalFlow.TARGET_ENTITY_KIND.eq(EntityKind.ACTOR.name()))
                        .and(logicalFlow.SOURCE_ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                        .and(logicalFlow.ENTITY_LIFECYCLE_STATUS.ne(REMOVED.name())))
                        .and(applicationConditions);

        Select<Record1<Long>> targetAppIds = DSL.select(logicalFlow.TARGET_ENTITY_ID)
                .from(logicalFlow)
                .innerJoin(APPLICATION).on(APPLICATION.ID.eq(logicalFlow.TARGET_ENTITY_ID))
                .where(logicalFlow.SOURCE_ENTITY_ID.eq(actorId)
                        .and(logicalFlow.SOURCE_ENTITY_KIND.eq(EntityKind.ACTOR.name()))
                        .and(logicalFlow.TARGET_ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                        .and(logicalFlow.ENTITY_LIFECYCLE_STATUS.ne(REMOVED.name())))
                        .and(applicationConditions);

        return sourceAppIds
                .union(targetAppIds);
    }


    private Select<Record1<Long>> mkForFlowDiagram(ApplicationIdSelectionOptions options) {
        ensureScopeIsExact(options);

        Condition applicationConditions = mkApplicationConditions(options);
        return DSL.select(flowDiagram.ENTITY_ID)
                .from(flowDiagram)
                .innerJoin(APPLICATION)
                    .on(APPLICATION.ID.eq(flowDiagram.ENTITY_ID))
                .where(flowDiagram.DIAGRAM_ID.eq(options.entityReference().id()))
                .and(flowDiagram.ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                .and(applicationConditions);
    }


    private Select<Record1<Long>> mkForMeasurable(ApplicationIdSelectionOptions options) {
        Select<Record1<Long>> measurableSelector = measurableIdSelectorFactory.apply(options);

        Condition applicationConditions = mkApplicationConditions(options);

        return dsl.select(measurableRating.ENTITY_ID)
                .from(measurableRating)
                .innerJoin(APPLICATION)
                    .on(APPLICATION.ID.eq(measurableRating.ENTITY_ID))
                .where(measurableRating.ENTITY_KIND.eq(DSL.val(EntityKind.APPLICATION.name())))
                .and(measurableRating.MEASURABLE_ID.in(measurableSelector))
                .and(applicationConditions);
    }


    private Select<Record1<Long>> mkForApplication(ApplicationIdSelectionOptions options) {
        checkTrue(options.scope() == EXACT, "Can only create selector for exact matches if given an APPLICATION ref");
        return DSL.select(DSL.val(options.entityReference().id()));
    }


    private Select<Record1<Long>> mkForEntityRelationship(ApplicationIdSelectionOptions options) {
        ensureScopeIsExact(options);

        Condition applicationConditions = mkApplicationConditions(options);

        Select<Record1<Long>> appToEntity = DSL.selectDistinct(ENTITY_RELATIONSHIP.ID_A)
                .from(ENTITY_RELATIONSHIP)
                .innerJoin(APPLICATION)
                    .on(APPLICATION.ID.eq(ENTITY_RELATIONSHIP.ID_A))
                .where(ENTITY_RELATIONSHIP.KIND_A.eq(EntityKind.APPLICATION.name()))
                .and(ENTITY_RELATIONSHIP.KIND_B.eq(options.entityReference().kind().name()))
                .and(ENTITY_RELATIONSHIP.ID_B.eq(options.entityReference().id()))
                .and(applicationConditions);

        Select<Record1<Long>> entityToApp = DSL.selectDistinct(ENTITY_RELATIONSHIP.ID_B)
                .from(ENTITY_RELATIONSHIP)
                .innerJoin(APPLICATION)
                    .on(APPLICATION.ID.eq(ENTITY_RELATIONSHIP.ID_B))
                .where(ENTITY_RELATIONSHIP.KIND_B.eq(EntityKind.APPLICATION.name()))
                .and(ENTITY_RELATIONSHIP.KIND_A.eq(options.entityReference().kind().name()))
                .and(ENTITY_RELATIONSHIP.ID_A.eq(options.entityReference().id()))
                .and(applicationConditions);


        return appToEntity
                .union(entityToApp);
    }


    private SelectConditionStep<Record1<Long>> mkForOrgUnit(ApplicationIdSelectionOptions options) {

        ImmutableIdSelectionOptions ouSelectorOptions = ImmutableIdSelectionOptions.builder()
                .entityReference(options.entityReference())
                .scope(options.scope())
                .build();

        Select<Record1<Long>> ouSelector = orgUnitIdSelectorFactory.apply(ouSelectorOptions);

        Condition applicationConditions = mkApplicationConditions(options);

        return dsl
                .selectDistinct(APPLICATION.ID)
                .from(APPLICATION)
                .where(APPLICATION.ORGANISATIONAL_UNIT_ID.in(ouSelector))
                .and(applicationConditions);
    }


    private SelectOrderByStep<Record1<Long>> mkForAppGroup(ApplicationIdSelectionOptions options) {
        if (options.scope() != EXACT) {
            throw new UnsupportedOperationException(
                    "App Groups are not hierarchical therefore ignoring requested scope of: " + options.scope());
        }

        Condition applicationConditions = mkApplicationConditions(options);

        SelectConditionStep<Record1<Long>> associatedOrgUnits = DSL
                .selectDistinct(ENTITY_HIERARCHY.ID)
                .from(appGroupOuEntry)
                .innerJoin(ENTITY_HIERARCHY)
                .on(ENTITY_HIERARCHY.ANCESTOR_ID.eq(appGroupOuEntry.ORG_UNIT_ID)
                        .and(ENTITY_HIERARCHY.KIND.eq(EntityKind.ORG_UNIT.name())))
                .where(appGroupOuEntry.GROUP_ID.eq(options.entityReference().id()));

        SelectConditionStep<Record1<Long>> applicationIdsFromAssociatedOrgUnits = DSL
                .select(APPLICATION.ID)
                .from(APPLICATION)
                .innerJoin(ORGANISATIONAL_UNIT)
                .on(APPLICATION.ORGANISATIONAL_UNIT_ID.eq(ORGANISATIONAL_UNIT.ID))
                .where(ORGANISATIONAL_UNIT.ID.in(associatedOrgUnits));

        SelectConditionStep<Record1<Long>> directApps = DSL
                .select(appGroupAppEntry.APPLICATION_ID)
                .from(appGroupAppEntry)
                .where(appGroupAppEntry.GROUP_ID.eq(options.entityReference().id()));

        return dsl
                .select(APPLICATION.ID)
                .from(APPLICATION)
                .where(APPLICATION.ID.in(directApps.unionAll(applicationIdsFromAssociatedOrgUnits)))
                .and(applicationConditions);
    }


    private Select<Record1<Long>> mkForPerson(ApplicationIdSelectionOptions options) {
        switch (options.scope()) {
            case EXACT:
                return mkForSinglePerson(options);
            case CHILDREN:
                return mkForPersonReportees(options);
            default:
                throw new UnsupportedOperationException(
                        "Querying for appIds of person using (scope: '"
                                + options.scope()
                                + "') not supported");
        }
    }


    private Select<Record1<Long>> mkForPersonReportees(ApplicationIdSelectionOptions options) {

        Select<Record1<String>> emp = dsl.select(person.EMPLOYEE_ID)
                .from(person)
                .where(person.ID.eq(options.entityReference().id()));

        SelectConditionStep<Record1<String>> reporteeIds = DSL.selectDistinct(personHierarchy.EMPLOYEE_ID)
                .from(personHierarchy)
                .where(personHierarchy.MANAGER_ID.eq(emp));

        Condition applicationConditions = mkApplicationConditions(options);
        Condition condition = involvement.ENTITY_KIND.eq(EntityKind.APPLICATION.name())
                .and(involvement.EMPLOYEE_ID.eq(emp)
                        .or(involvement.EMPLOYEE_ID.in(reporteeIds)))
                .and(applicationConditions);

        return dsl
                .selectDistinct(involvement.ENTITY_ID)
                .from(involvement)
                .innerJoin(APPLICATION)
                    .on(APPLICATION.ID.eq(involvement.ENTITY_ID))
                .where(condition);
    }


    private Select<Record1<Long>> mkForSinglePerson(ApplicationIdSelectionOptions options) {

        Select<Record1<String>> employeeId = dsl.select(person.EMPLOYEE_ID)
                .from(person)
                .where(person.ID.eq(options.entityReference().id()));

        Condition applicationConditions = mkApplicationConditions(options);
        return dsl
                .selectDistinct(involvement.ENTITY_ID)
                .from(involvement)
                .innerJoin(APPLICATION)
                    .on(APPLICATION.ID.eq(involvement.ENTITY_ID))
                .where(involvement.ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                .and(involvement.EMPLOYEE_ID.eq(employeeId))
                .and(applicationConditions);
    }


    private Select<Record1<Long>> mkForDataType(ApplicationIdSelectionOptions options) {
        Select<Record1<Long>> dataTypeSelector = dataTypeIdSelectorFactory.apply(options);

        Condition condition = LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID.in(dataTypeSelector)
                .and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND.eq(EntityKind.DATA_TYPE.name()));

        Field appId = DSL.field("app_id", Long.class);

        Condition applicationConditions = mkApplicationConditions(options);

        SelectConditionStep<Record1<Long>> sources = selectLogicalFlowAppsByDataType(
                LOGICAL_FLOW.SOURCE_ENTITY_ID.as(appId),
                condition
                        .and(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                        .and(applicationConditions),
                LOGICAL_FLOW.SOURCE_ENTITY_ID);

        SelectConditionStep<Record1<Long>> targets = selectLogicalFlowAppsByDataType(
                LOGICAL_FLOW.TARGET_ENTITY_ID.as(appId),
                condition
                        .and(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                        .and(applicationConditions),
                LOGICAL_FLOW.TARGET_ENTITY_ID);

        return dsl
                .selectDistinct(appId)
                .from(sources)
                .union(targets);
    }


    private SelectConditionStep<Record1<Long>> selectLogicalFlowAppsByDataType(Field<Long> appField, Condition condition, Field<Long> joinField) {
        return dsl
                .select(appField)
                .from(LOGICAL_FLOW)
                .innerJoin(LOGICAL_FLOW_DECORATOR)
                    .on(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.eq(LOGICAL_FLOW.ID))
                .innerJoin(APPLICATION)
                    .on(APPLICATION.ID.eq(joinField))
                .where(condition)
                .and(NOT_REMOVED);
    }

}