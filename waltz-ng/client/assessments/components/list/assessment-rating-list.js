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

import {initialiseData} from "../../../common";
import _ from "lodash";
import template from "./assessment-rating-list.html";


const bindings = {
    assessments: "<",
    onSelect: "<"
};


const initialState = {
    assessmentsWithRatings: [],
    assessmentsWithoutRatings: []
};


function controller() {
    const vm = initialiseData(this, initialState);

    vm.$onChanges = () => {
        if (vm.assessments) {
            const partitioned = _.partition(
                vm.assessments,
                assessment => _.isNil(assessment.rating));
            vm.assessmentsWithoutRatings = _.sortBy(partitioned[0], d => d.definition.name);
            vm.assessmentsWithRatings = _.sortBy(partitioned[1], d => d.definition.name);
        }
    };

}


controller.$inject = [
];


const component = {
    template,
    bindings,
    controller
};


export default {
    component,
    id: "waltzAssessmentRatingList"
};
