/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016  Khartec Ltd.
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

import {initialiseData} from '../common';
import {toGraphId} from '../flow-diagram/flow-diagram-utils';
import _ from 'lodash';


const template = require('./physical-specification-view.html');


const initialState = {
    visibility: {
        createReportOverlay: false,
        createReportButton: true,
        createReportBusy: false
    },
    createReportForm: {
        name: ""
    },
    selectedSpecDefinition: {},
    specDefinitions: [],
    specDefinitionCreate: {
        creating: false
    }
};


const addToHistory = (historyStore, spec) => {
    if (! spec) { return; }
    historyStore.put(
        spec.name,
        'PHYSICAL_SPECIFICATION',
        'main.physical-specification.view',
        { id: spec.id });
};



function loadFlowDiagrams(specId, $q, flowDiagramStore, flowDiagramEntityStore) {
    const ref = {
        id: specId,
        kind: 'PHYSICAL_SPECIFICATION'
    };

    const selector = {
        entityReference: ref,
        scope: 'EXACT'
    };

    const promises = [
        flowDiagramStore.findForSelector(selector),
        flowDiagramEntityStore.findForSelector(selector)
    ];
    return $q
        .all(promises)
        .then(([flowDiagrams, flowDiagramEntities]) => ({ flowDiagrams, flowDiagramEntities }));
}


const mkReleaseLifecycleStatusChangeCommand = (newStatus) => {
    return { newStatus };
};



function controller($q,
                    $stateParams,
                    applicationStore,
                    bookmarkStore,
                    changeLogStore,
                    flowDiagramStore,
                    flowDiagramEntityStore,
                    historyStore,
                    logicalFlowStore,
                    notification,
                    orgUnitStore,
                    physicalSpecDefinitionStore,
                    physicalSpecDefinitionFieldStore,
                    physicalSpecDefinitionSampleFileStore,
                    physicalSpecificationStore,
                    physicalFlowStore)
{
    const vm = initialiseData(this, initialState);

    const specId = $stateParams.id;
    const ref = {
        kind: 'PHYSICAL_SPECIFICATION',
        id: specId
    };

    // -- LOAD ---

    physicalSpecificationStore
        .getById(specId)
        .then(spec => vm.specification = spec)
        .then(spec => applicationStore.getById(spec.owningEntity.id))
        .then(app => vm.owningEntity = app)
        .then(app => orgUnitStore.getById(app.organisationalUnitId))
        .then(ou => vm.organisationalUnit = ou)
        .then(() => addToHistory(historyStore, vm.specification));

    physicalFlowStore
        .findBySpecificationId(specId)
        .then(physicalFlows => vm.physicalFlows = physicalFlows);

    logicalFlowStore
        .findBySelector({ entityReference: ref, scope: 'EXACT'})
        .then(logicalFlows => {
            vm.logicalFlows = logicalFlows;
            vm.logicalFlowsById = _.keyBy(logicalFlows, 'id')
        });

    changeLogStore
        .findByEntityReference(ref)
        .then(changeLogs => vm.changeLogs = changeLogs);

    const loadSpecDefinitions = () => physicalSpecDefinitionStore
        .findForSpecificationId(specId)
        .then(specDefs => vm.specDefinitions = specDefs)
        .then(specDefs => {
            const activeSpec = _.find(specDefs, { status: 'ACTIVE'});
            if (activeSpec) vm.selectSpecDefinition(activeSpec);
        });

    loadSpecDefinitions();


    vm.loadFlowDiagrams = () => {
        loadFlowDiagrams(specId, $q, flowDiagramStore, flowDiagramEntityStore)
            .then(r => Object.assign(vm, r));
    };

    vm.loadFlowDiagrams();


    bookmarkStore
        .findByParent(ref)
        .then(bs => vm.bookmarks = bs);

    vm.selectSpecDefinition = (def) => {
        const specDefFieldPromise = physicalSpecDefinitionFieldStore
            .findForSpecDefinitionId(def.id);

        const specDefSampleFilePromise = physicalSpecDefinitionSampleFileStore
            .findForSpecDefinitionId(def.id);

        $q.all([specDefFieldPromise, specDefSampleFilePromise])
            .then(([fields, file]) => {
                vm.selectedSpecDefinition.def = def;
                vm.selectedSpecDefinition.fields = fields;
                vm.selectedSpecDefinition.sampleFile = file;
            });
    };


    vm.showCreateSpecDefinition = () => {
        vm.specDefinitionCreate.creating = true;
    };


    vm.hideCreateSpecDefinition = () => {
        vm.specDefinitionCreate.creating = false;
    };


    vm.createSpecDefinition = (specDef) => {
        physicalSpecDefinitionStore
            .create(specId, specDef.def)
            .then(specDefId => physicalSpecDefinitionFieldStore
                .createFields(specDefId, specDef.fields))
            .then(r => {
                notification.success('Specification definition created successfully');
                loadSpecDefinitions();
                vm.hideCreateSpecDefinition();
            }, r => {
                notification.error("Failed to create specification definition. Ensure that 'version' is unique");
            });
    };

    vm.createFlowDiagramCommands = () => {
        const nodeCommands = _
            .chain(vm.logicalFlows)
            .map(f => { return [f.source, f.target]; })
            .flatten()
            .uniqBy(toGraphId)
            .map(a => ({ command: 'ADD_NODE', payload: a }))
            .value();

        const flowCommands = _.map(
            vm.logicalFlows,
            f => ({ command: 'ADD_FLOW', payload: Object.assign({}, f, { kind: 'LOGICAL_DATA_FLOW' } )}));

        const moveCommands = _.map(
            nodeCommands,
            (nc, idx) => {
                return {
                    command: 'MOVE',
                    payload: {
                        id : toGraphId(nc.payload),
                        dx: 50 + (110 * (idx % 8)),
                        dy: 10 + (50 * (idx / 8))
                    }
                };
            });

        const physFlowCommands = _.map(
            vm.physicalFlows,
            pf => {
                return {
                    command: 'ADD_DECORATION',
                    payload: {
                        ref: { kind: 'LOGICAL_DATA_FLOW', id: pf.logicalFlowId },
                        decoration: Object.assign({}, pf, { kind: 'PHYSICAL_FLOW' })
                    }
                };
            });

        const title = `${vm.specification.name} Flows`;

        const titleCommands = [
            { commands: 'SET_TITLE', payload: title }
        ];

        return _.concat(nodeCommands, flowCommands, physFlowCommands, moveCommands, titleCommands);
    };

    vm.deleteSpec = (specDef) => {
        physicalSpecDefinitionStore
            .deleteSpecification(specDef.id)
            .then(result => {
                if (result) {
                    notification.success(`Deleted version ${specDef.version}`);
                    loadSpecDefinitions();
                } else {
                    notification.error(`Could not delete version ${specDef.version}`);
                }
            })
    };

    vm.activateSpec = (specDef) => {
        physicalSpecDefinitionStore
            .updateStatus(specDef.id, mkReleaseLifecycleStatusChangeCommand('ACTIVE'))
            .then(result => {
                if (result) {
                    notification.success(`Marked version ${specDef.version} as active`);
                    loadSpecDefinitions();
                } else {
                    notification.error(`Could not mark version ${specDef.version} as active`);
                }
            })
    };

    vm.markSpecObsolete = (specDef) => {
        physicalSpecDefinitionStore
            .updateStatus(specDef.id, mkReleaseLifecycleStatusChangeCommand('OBSOLETE'))
            .then(result => {
                if (result) {
                    notification.success(`Marked version ${specDef.version} as obsolete`);
                    loadSpecDefinitions();
                } else {
                    notification.error(`Could not mark version ${specDef.version} as obsolete`);
                }
            })
    };

}


controller.$inject = [
    '$q',
    '$stateParams',
    'ApplicationStore',
    'BookmarkStore',
    'ChangeLogStore',
    'FlowDiagramStore',
    'FlowDiagramEntityStore',
    'HistoryStore',
    'LogicalFlowStore',
    'Notification',
    'OrgUnitStore',
    'PhysicalSpecDefinitionStore',
    'PhysicalSpecDefinitionFieldStore',
    'PhysicalSpecDefinitionSampleFileStore',
    'PhysicalSpecificationStore',
    'PhysicalFlowStore'
];


export default {
    template,
    controller,
    controllerAs: 'ctrl'
};