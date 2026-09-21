import { n as e } from "./chunk-DiqZc92J.js";
import { _ as t, g as n } from "./icons-wGoYEurg.js";
//#region frontend/copilot/shared/stats.ts
var r, i, a, o, s, c, l, u = e((() => {
	n(), r = (() => {
		let e = window;
		return e.Vaadin ??= {}, e.Vaadin.copilot ??= {}, e.Vaadin.copilot._trackEventInterceptors ??= /* @__PURE__ */ new Set(), e.Vaadin.copilot._trackEventInterceptors;
	})(), i = (e) => {
		r.add(e);
	}, a = (e) => {
		r.delete(e);
	}, o = () => {
		t("copilot-browser-info", {
			userAgent: navigator.userAgent,
			locale: navigator.language,
			timezone: Intl.DateTimeFormat().resolvedOptions().timeZone
		});
	}, s = (e, n) => {
		let i = n ? { ...n } : {};
		[...r].forEach((t) => {
			try {
				t(e, i);
			} catch {}
		}), t("copilot-track-event", {
			event: e,
			properties: Object.keys(i).length ? i : void 0
		});
	}, c = (e, t) => {
		s(e, {
			...t,
			view: "react"
		});
	}, l = (e, t) => {
		s(e, {
			...t,
			view: "flow"
		});
	};
}));
//#endregion
export { s as a, o as i, u as n, l as o, a as r, c as s, i as t };
