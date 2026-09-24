import { n as e } from "./chunk-DiqZc92J.js";
import { H as t, U as n, at as r, ht as i, it as a, n as o, o as s, r as c, t as l, u, ut as d } from "./icons-wGoYEurg.js";
import { l as f, o as p } from "./consts-B_BO2mll.js";
import { a as m, d as h, i as g, l as _, o as v, r as y, s as b } from "./section-panel-ui-state-Dbt9i-HL.js";
import { n as x, r as S } from "./copilot-ui-state-Dc6l_5DA.js";
import { n as C, t as w } from "./copilot-message-box-krJnoslR.js";
import { a as T, c as E, i as D, l as O, o as k, u as A } from "./copilot-error-handler-B8dKSldg.js";
import { n as j, t as M } from "./copilot-stored-machine-state-DS3t0BPl.js";
import { n as N, r as P } from "./copilot-notification-B9WA4gMj.js";
import { n as F, t as I } from "./base-panel-B2biyt0G.js";
//#region frontend/copilot/plugins/copilot-vaadin-versions/vaadin-version-request.ts
function L() {
	x.setVaadinVersionState({ loading: !0 }), n(`${p}get-new-vaadin-versions`, { includePreReleases: j.getNewVersionPreReleasesVisible() }, (e) => {
		let t = e.data;
		if (t.error) {
			x.setVaadinVersionState({
				loading: !1,
				errorMessage: t.error.message,
				hasError: !0,
				versions: []
			});
			return;
		}
		if (!t.newVersions) return;
		let n = JSON.parse(t.newVersions);
		x.setVaadinVersionState({
			versions: n,
			loading: !1,
			hasError: !1
		});
	});
}
var R = e((() => {
	t(), f(), M(), S(), d(), i(() => j.getNewVersionPreReleasesVisible(), () => {
		L();
	}, { fireImmediately: !0 });
})), z, B, V;
//#endregion
e((() => {
	b(), r(), c(), F(), S(), g(), o(), M(), t(), f(), N(), w(), R(), k(), E(), v(), z = class extends I {
		constructor(...e) {
			super(...e), this.renderIcon = (e) => e.name === "Flow" ? l.flow : e.name === "Hilla" ? l.hilla : e.name === "Web Components" || e.name === "Flow Components" ? l.uiComponents : e.name === "TestBench" ? l.checklist : e.name.indexOf("MPR") === -1 ? e.name.indexOf("AppSec") === -1 ? e.name.indexOf("Collaboration") === -1 ? e.name === "Copilot" || e.name === "CoPilot" ? l.copilot : e.name.indexOf("Kubernetes") === -1 ? e.name.indexOf("Observability") === -1 ? e.name.indexOf("SSO") === -1 ? e.name.indexOf("Modernization") === -1 ? l.code : l.rocketLaunch : l.login : l.visibility : l.kubernetes : l.group : l.verifiedUser : l.playCircle;
		}
		render() {
			return u`
      <ul class="list-none m-0 pb-2.5 px-2">
        ${this.renderContent()}
      </ul>
    `;
		}
		renderContent() {
			return x.newVaadinVersionState === void 0 || x.newVaadinVersionState.loading ? C("loading", "Versions are loading...") : x.newVaadinVersionState.hasError ? C("error", x.newVaadinVersionState.errorMessage ?? "Unable to display new versions") : !x.newVaadinVersionState.versions || x.newVaadinVersionState.versions.length === 0 ? C("success", "Vaadin version is up to date") : u`
      ${x.newVaadinVersionState.versions.map((e, t) => this.renderNewVersionItem(e, t === 0))}
    `;
		}
		renderNewVersionItem(e, t) {
			return u`
      <li>
        <vaadin-details ?opened="${t}">
          <vaadin-details-summary class="relative" slot="summary">
            <div class="flex gap-2 items-center">
              ${e.version}
              ${t ? u`<span
                    class="bg-blue-3 dark:bg-blue-7 font-normal inline-flex px-1.5 py-px rounded-full text-xs text-blue-11 dark:text-blue-12"
                    >Latest</span
                  >` : s}
              <vaadin-button
                aria-label="Update"
                class="absolute end-0 top-0"
                theme="icon tertiary"
                ?disabled="${this.updateClickedVersion !== void 0}"
                @click="${(t) => {
				t.stopPropagation(), this.sendUpdateRequest(e.version, e.preRelease);
			}}">
                <vaadin-icon
                  class="${this.updateClickedVersion === e.version ? "animate-spin" : ""}"
                  .svg="${this.updateClickedVersion === e.version ? l.progressActivity : l.upgrade}"></vaadin-icon>
                <vaadin-tooltip slot="tooltip" text="Update"></vaadin-tooltip>
              </vaadin-button>
            </div>
          </vaadin-details-summary>
          <ul class="border-dashed divide-y list-none m-0 pe-2 ps-8">
            ${e.changelogs.map((e) => u`
                <li class="flex gap-2 py-2">
                  <vaadin-icon .svg="${this.renderIcon(e)}"></vaadin-icon>
                  ${e.name}
                  <a href="${e.url}" target="_blank">${e.version}</a>
                </li>
              `)}
          </ul>
        </vaadin-details>
      </li>
    `;
		}
		sendUpdateRequest(e, t) {
			this.updateClickedVersion = e;
			let r = {
				newVersion: e,
				preRelease: t
			};
			n(`${p}update-vaadin-version`, r, (e) => {
				this.updateClickedVersion = void 0;
				let t = !T(e.data, r);
				if (t) {
					let e = "Please restart the server";
					A() && (e = "Server will be restarted by the IDE plugin", O()), P({
						message: "Version updated",
						type: a.INFORMATION,
						details: e
					});
				}
				return t;
			}).catch((e) => {
				D("Error updating version", e);
			});
		}
	}, m([_()], z.prototype, "updateClickedVersion", void 0), z = m([h("copilot-vaadin-versions")], z), B = class extends y {
		createRenderRoot() {
			return this;
		}
		render() {
			let e = j.getNewVersionPreReleasesVisible();
			return u`<vaadin-button
      aria-pressed="${e}"
      theme="tertiary"
      @click="${(e) => {
				e.stopPropagation(), j.setNewVersionPreReleasesVisible(!j.getNewVersionPreReleasesVisible());
			}}">
      <vaadin-icon slot="prefix" .svg="${e ? l.visibility : l.visibilityOff}"></vaadin-icon>
      Prereleases
    </vaadin-button>`;
		}
	}, B = m([h("copilot-vaadin-versions-actions")], B), V = {
		header: "Vaadin Versions",
		tag: "copilot-vaadin-versions",
		actionsTag: "copilot-vaadin-versions-actions"
	}, window.Vaadin.copilot.plugins.push({ init(e) {
		e.addPanel(V);
	} });
}))();
export { z as CopilotVaadinVersions, B as CopilotVersionCheckerActions, V as versionCheckerPanel };
