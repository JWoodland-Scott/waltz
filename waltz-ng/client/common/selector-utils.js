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


import {checkIsEntityRef} from "./checks";

export function determineDownwardsScopeForKind(kind) {
    switch (kind) {
        case "ACTOR":
        case "APPLICATION":
        case "APP_GROUP":
        case "CHANGE_INITIATIVE":
        case "FLOW_DIAGRAM":
        case "LICENCE":
        case "LOGICAL_DATA_ELEMENT":
        case "LOGICAL_FLOW":
        case "PHYSICAL_FLOW":
        case "PHYSICAL_SPECIFICATION":
        case "SCENARIO":
        case "SERVER":
        case "SOFTWARE":
        case "SOFTWARE_VERSION":
            return "EXACT";
        default:
            return "CHILDREN";
    }
}


export function determineUpwardsScopeForKind(kind) {
    switch (kind) {
        case "ORG_UNIT":
        case "MEASURABLE":
        case "DATA_TYPE":
        case "CHANGE_INITIATIVE":
            return "PARENTS";
        default:
            return "EXACT";
    }
}


/**
 * Helper method to construct valid IdSelectionOption instances.
 * @param entityReference
 * @param scope
 * @param entityLifecycleStatuses
 * @param filters
 * @returns {{entityLifecycleStatuses: string[], entityReference: {kind: *, id: *}, scope: (*|string), filters}}
 */
export function mkSelectionOptions(entityReference, scope, entityLifecycleStatuses = ["ACTIVE"], filters = {}) {
    checkIsEntityRef(entityReference);

    return {
        entityReference: { id: entityReference.id, kind: entityReference.kind }, // use minimal ref to increase cache hits in broker
        scope: scope || determineDownwardsScopeForKind(entityReference.kind),
        entityLifecycleStatuses,
        filters
    };
}


/**
 * @deprecated use mkSelectionOptions instead, this method now  just calls that one.
 * TODO: Remove in 1.23
 *
 * @param entityReference
 * @param scope
 * @param entityLifecycleStatuses
 * @param filters
 * @returns {{entityLifecycleStatuses: string[], entityReference: {kind: *, id: *}, scope: (*|string), filters}}
 */
export function mkApplicationSelectionOptions(entityReference,
                                              scope,
                                              entityLifecycleStatuses = ["ACTIVE"],
                                              filters = {}) {

    console.log("Calls to mkApplicationSelectionOptions are deprecated, calling mkSelectionOptions instead");
    // debugger;
    return mkSelectionOptions(entityReference, scope, entityLifecycleStatuses, filters);
}