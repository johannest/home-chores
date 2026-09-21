import { n as e } from "./chunk-DiqZc92J.js";
import { at as t, it as n, n as r, o as i, r as a, t as o, u as s } from "./icons-wGoYEurg.js";
import { a as c, d as l, i as u, l as d, o as f, r as p, s as m } from "./section-panel-ui-state-Dbt9i-HL.js";
import { n as h, r as g } from "./copilot-ui-state-Dc6l_5DA.js";
import { a as _, n as v } from "./stats-rEPv3bOZ.js";
import { c as y, l as b, o as x, r as S, u as C } from "./copilot-error-handler-BcNCQDnH.js";
import { n as w, t as T } from "./copilot-stored-machine-state-DS3t0BPl.js";
import { n as E, r as D } from "./copilot-notification-B9WA4gMj.js";
import { n as O, t as k } from "./base-panel-DcZrefqb.js";
import { r as A, t as j } from "./copilot-experimental-features-Cu_zexjO.js";
//#region frontend/copilot/plugins/copilot-features/copilot-features-plugin.ts
var M, N, P, F, I;
//#endregion
e((() => {
	x(), v(), a(), m(), A(), E(), t(), T(), g(), y(), r(), u(), O(), f(), M = window.Vaadin.devTools, N = "copilotExperimentalFeatures", P = class extends k {
		constructor(...e) {
			super(...e), this.toggledFeaturesThatAreRequiresServerRestart = [];
		}
		connectedCallback() {
			super.connectedCallback(), this.classList.add("contents");
		}
		render() {
			let e = h.featureFlags.find((e) => e.id === N);
			return s`
      <vaadin-tabsheet class="border-0" theme="no-padding">
        <vaadin-tabs slot="tabs">
          <vaadin-tab id="application-features-tab">Application</vaadin-tab>
          <vaadin-tab id="copilot-features-tab">Copilot</vaadin-tab>
        </vaadin-tabs>
        <div tab="application-features-tab" class="px-4 py-0.5">
          <div class="border-dashed flex flex-col divide-y">
            ${h.featureFlags.filter((e) => e.id !== N).sort((e, t) => e.title.localeCompare(t.title)).map((e) => this.renderFeatureFlag(e))}
          </div>
        </div>
        <div tab="copilot-features-tab" class="px-4 py-0.5">
          <div class="border-dashed flex flex-col divide-y">
            ${e ? this.renderFeatureFlag(e) : i}
            ${this.renderCopilotExperimentalFeatures()}
          </div>
        </div>
      </vaadin-tabsheet>
    `;
		}
		renderFeatureFlag(e) {
			return this.renderFeatureRow({
				id: e.id,
				label: e.title,
				description: s`
        <a
          class="flex gap-0.5 text-xs"
          href="${e.moreInfoLink}"
          id="${e.id}-desc"
          target="_blank"
          rel="noopener noreferrer"
          >More info<vaadin-icon class="icon-sm" .svg="${o.arrowOutward}"></vaadin-icon
        ></a>
      `,
				checked: e.enabled,
				onChange: (t) => this.toggleFeatureFlag(t, e)
			});
		}
		renderCopilotExperimentalFeatures() {
			let e = h.userInfo?.copilotExperimentalFeatureFlag === !0;
			return j.filter((e) => e.available()).sort((e, t) => e.name.localeCompare(t.name)).map((t) => this.renderFeatureRow({
				id: t.id,
				label: t.name,
				description: s`
            <span class="text-xs text-secondary" id="${t.id}-desc"
              >${t.description}</span
            >
          `,
				checked: w.isExperimentalFeatureEnabled(t),
				disabled: !e,
				onChange: (e) => this.toggleExperimentalFeatureFlag(e, t)
			}));
		}
		renderFeatureRow(e) {
			return s`
      <div class="flex gap-2 justify-between py-3.5">
        <div class="flex flex-col ${e.disabled ? "opacity-50" : ""}">
          <label id="${e.id}-label">${e.label}</label>
          ${e.description}
        </div>
        <copilot-toggle-button
          accessible-name-ref="${e.id}-label"
          accessible-desc-ref="${e.id}-desc"
          ?checked=${e.checked}
          ?disabled=${e.disabled ?? !1}
          @on-change=${e.onChange}>
        </copilot-toggle-button>
      </div>
    `;
		}
		toggleFeatureFlag(e, t) {
			let r = e.target.checked;
			_("use-feature", {
				source: "toggle",
				enabled: r,
				id: t.id
			}), M.frontendConnection ? (M.frontendConnection.send("setFeature", {
				featureId: t.id,
				enabled: r
			}), t.requiresServerRestart && h.toggleServerRequiringFeatureFlag(t), D({
				type: n.INFORMATION,
				message: `“${t.title}” ${r ? "enabled" : "disabled"}`,
				details: t.requiresServerRestart ? S() : void 0,
				dismissId: `feature${t.id}${r ? "Enabled" : "Disabled"}`
			}), t.id === N && h.userInfo && h.setUserInfo({
				...h.userInfo,
				copilotExperimentalFeatureFlag: r
			})) : M.log("error", `Unable to toggle feature ${t.title}: No server connection available`);
		}
		toggleExperimentalFeatureFlag(e, t) {
			let n = e.target.checked;
			_("use-experimental-feature", {
				source: "toggle",
				enabled: n,
				id: t.id
			});
			let r = w.isExperimentalFeatureEnabled(t);
			w.setExperimentalFeatureEnabled(t, n), t.requiresReload && n && !r && window.location.reload();
		}
	}, c([d()], P.prototype, "toggledFeaturesThatAreRequiresServerRestart", void 0), P = c([l("copilot-features-panel")], P), F = class extends p {
		constructor(...e) {
			super(...e), this.serverRestarting = !1;
		}
		createRenderRoot() {
			return this;
		}
		render() {
			if (h.serverRestartRequiringToggledFeatureFlags.length === 0 || !C()) return i;
			let e = this.serverRestarting ? "Restarting..." : "Click to restart server";
			return s`
      <vaadin-button
        aria-label="Restart server"
        ?disabled="${this.serverRestarting}"
        theme="icon tertiary"
        @click=${() => {
				this.serverRestarting = !0, b();
			}}>
        <vaadin-icon .svg="${o.refresh}"></vaadin-icon>
        <vaadin-tooltip slot="tooltip" text=${e}></vaadin-tooltip>
      </vaadin-button>
    `;
		}
	}, c([d()], F.prototype, "serverRestarting", void 0), F = c([l("copilot-features-actions")], F), I = {
		header: "Features",
		tag: "copilot-features-panel",
		helpUrl: "https://vaadin.com/docs/latest/flow/configuration/feature-flags",
		actionsTag: "copilot-features-actions",
		toolbarOptions: {
			allowedModesWithOrder: { common: 0 },
			iconKey: "listAlt"
		}
	}, window.Vaadin.copilot.plugins.push({ init(e) {
		e.addPanel(I);
	} });
}))();
export { F as CopilotFeaturesActions, P as CopilotFeaturesPanel };
