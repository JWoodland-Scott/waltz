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

import _ from "lodash";
import {
    environmentColorScale,
    maturityColorScale,
    operatingSystemColorScale,
    variableScale
} from "../../common/colors";
import { endOfLifeStatus } from "../../common/services/enums/end-of-life-status";
import template from "./group-technology-summary.html";


const bindings = {
    stats: "<",
    parentEntityRef: "<"
};

const initialState = {
    environmentDescription: "",
    visibility: {
        servers: false,
        software: false,
        databases: false
    }
};


const PIE_SIZE = 70;


const PIE_CONFIG = {
    environment: {
        size: PIE_SIZE,
        colorProvider: (d) => environmentColorScale(d.key)
    },
    operatingSystem: {
        size: PIE_SIZE,
        colorProvider: (d) => operatingSystemColorScale(d.key)
    },
    location: {
        size: PIE_SIZE,
        colorProvider: (d) => variableScale(d.key)
    },
    vendor: {
        size: PIE_SIZE,
        colorProvider: (d) => variableScale(d.key)
    },
    maturity: {
        size: PIE_SIZE,
        colorProvider: (d) => maturityColorScale(d.key)
    },
    endOfLifeStatus: {
        size: PIE_SIZE,
        colorProvider: (d) => variableScale(d.key),
        labelProvider: (d) => endOfLifeStatus[d.key] ? endOfLifeStatus[d.key].name : "Unknown"
    }
};


const tallyToPieDatum = t => ({ key: t.id, count: t.count });
const prepareForPieChart = (tallies) => _.map(tallies, tallyToPieDatum);


function processServerStats(stats) {
    const serverStats = Object.assign({}, stats, {
        environment: prepareForPieChart(stats.environmentCounts),
        operatingSystem: prepareForPieChart(stats.operatingSystemCounts),
        location: prepareForPieChart(stats.locationCounts),
        operatingSystemEndOfLifeStatus: prepareForPieChart(stats.operatingSystemEndOfLifeStatusCounts),
        hardwareEndOfLifeStatus: prepareForPieChart(stats.hardwareEndOfLifeStatusCounts)
    });

    return {
        serverStats
    };
}

function processDatabaseStats(stats) {
    const databaseStats = Object.assign({}, stats, {
        environment: prepareForPieChart(stats.environmentCounts),
        vendor: prepareForPieChart(stats.vendorCounts),
        endOfLifeStatus: prepareForPieChart(stats.endOfLifeStatusCounts)
    });

    return {
        databaseStats
    };
}

function processSoftwareCatalogStats(stats) {
    const softwareStats = Object.assign({}, stats, {
        vendor: prepareForPieChart(stats.vendorCounts),
        maturity: prepareForPieChart(stats.maturityCounts)
    });
    return {
        softwareStats
    };
}


function calculateVisibility(stats) {
    return {
        servers: stats.serverStats.totalCount > 0,
        databases: stats.databaseStats && _.keys(stats.databaseStats.vendorCounts).length > 0,
        software: stats.softwareStats && _.keys(stats.softwareStats.vendorCounts).length > 0
    };
}

function controller() {

    const vm = _.defaultsDeep(this, initialState);

    vm.$onChanges = () => {
        if (! vm.stats) return;
        Object.assign(vm, processServerStats(vm.stats.serverStats));
        Object.assign(vm, processDatabaseStats(vm.stats.databaseStats));
        Object.assign(vm, processSoftwareCatalogStats(vm.stats.softwareStats));

        vm.visibility = calculateVisibility(vm.stats);

        const serverStats = vm.stats.serverStats;
        const totalServers = serverStats.totalCount;
        const totalEnvs = _.sumBy(serverStats.environmentCounts, d => d.count);


        vm.environmentDescription = totalEnvs > totalServers
            ? "Note: servers may support multiple environments"
            : "";

    };

    vm.pieConfig = PIE_CONFIG;
}


const component = {
    template,
    bindings,
    controller
};


export default {
    id: "waltzGroupTechnologySummary",
    component
};
