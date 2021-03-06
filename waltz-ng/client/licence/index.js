/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019  Waltz open source project
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
import { registerComponents, registerStores } from "../common/module-utils";
import LicenceStore from "./services/licence-store";

import Routes from "./routes";

import LicencePanel from "./components/panel/licence-panel";
import LicenceSection from "./components/section/licence-section";
import LicenceOverview from "./components/overview/licence-overview";

import LicenceList from "./pages/list/licence-list";
import LicenceView from "./pages/view/licence-view";


export default () => {

    const module = angular.module("waltz.licence", []);

    module
        .config(Routes);

    registerStores(
        module,
        [ LicenceStore ]);

    registerComponents(module, [
        LicencePanel,
        LicenceSection,
        LicenceOverview,

        LicenceList,
        LicenceView
    ]);

    return module.name;
};
