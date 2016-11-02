/*
 *  Waltz
 * Copyright (c) David Watkins. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 *
 */

import {initialiseData, invokeFunction} from "../../common";


const bindings = {
    allActors: '<',
    addLabel: '@',
    cancelLabel: '@',
    onCancel: '<',
    onAdd: '<',
    onSelect: '<'
};


const template = require('./basic-actor-selector.html');


const initialState = {
    addLabel: 'Add',
    cancelLabel: 'Cancel',
    onCancel: () => console.log('No onCancel provided to basic actor selector'),
    onAdd: (a) => console.log('No onAdd provided to basic actor selector', a),
    onSelect: (a) => console.log('No onSelect provided to basic actor selector', a)
};


function controller() {
    const vm = initialiseData(this, initialState);

    vm.add = (actor) => {
        if (! actor) return ;
        vm.onAdd(actor);
    };

    vm.cancel = () => vm.onCancel();

    vm.select = (actor) => {
        vm.selectedActor = actor;
        invokeFunction(vm.onSelect, actor);
    };
}


controller.$inject = [];


const component = {
    bindings,
    template,
    controller
};


export default component;

