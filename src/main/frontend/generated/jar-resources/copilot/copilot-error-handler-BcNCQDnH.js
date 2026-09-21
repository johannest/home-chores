import { n as e } from "./chunk-DiqZc92J.js";
import { A as t, C as n, D as r, H as i, U as a, at as o, it as s, l as c, mt as l, n as u, o as d, r as f, s as ee, t as te, u as p, ut as m } from "./icons-wGoYEurg.js";
import { l as ne, o as re } from "./consts-DLyL2h6i.js";
import { a as h, i as g, n as _, r as v } from "./copilot-ui-state-Dc6l_5DA.js";
import { n as ie } from "./stats-rEPv3bOZ.js";
import { i as ae, n as y, r as oe, t as se } from "./directive-CZ105jp5.js";
import { n as ce, t as b } from "./copilot-stored-machine-state-DS3t0BPl.js";
import { n as x } from "./copilot-notification-B9WA4gMj.js";
import { n as S, t as C } from "./early-project-state-D-4_8bD-.js";
//#region node_modules/lit-html/directives/unsafe-html.js
var w, T, E = e((() => {
	c(), oe(), w = class extends y {
		constructor(e) {
			if (super(e), this.it = d, e.type !== ae.CHILD) throw Error(this.constructor.directiveName + "() can only be used in child bindings");
		}
		render(e) {
			if (e === d || e == null) return this._t = void 0, this.it = e;
			if (e === ee) return e;
			if (typeof e != "string") throw Error(this.constructor.directiveName + "() called with a non-string value");
			if (e === this.it) return this._t;
			this.it = e;
			let t = [e];
			return t.raw = t, this._t = {
				_$litType$: this.constructor.resultType,
				strings: t,
				values: []
			};
		}
	}, w.directiveName = "unsafeHTML", w.resultType = 1, T = se(w);
})), D = e((() => {
	E();
}));
//#endregion
//#region frontend/copilot/shared/copilot-userinfo-util.ts
function O() {
	let e = _.userInfo;
	return !e || e.copilotProjectCannotLeaveLocalhost ? !1 : ce.isSendErrorReportsAllowed();
}
var k = e((() => {
	ie(), v(), $(), b(), r(), x(), o();
}));
//#endregion
//#region frontend/copilot/shared/hotswap-utils.ts
function A() {
	return _.idePluginState?.supportedActions?.find((e) => e === "restartApplication");
}
function j() {
	a(`${re}plugin-restart-application`, {}, () => {}).catch((e) => {
		I("Error restarting server", e);
	});
}
function le() {
	return C.jdkInfo?.jrebel || C.jdkInfo?.runningWitHotswap && C.jdkInfo?.runningWithExtendClassDef;
}
var M = e((() => {
	i(), ne(), $(), h(), x(), o(), v(), S();
}));
//#endregion
//#region frontend/copilot/shared/copilot-error-handler.ts
function N(e) {
	if (e === void 0) return !1;
	let t = Object.keys(e);
	return t.length === 1 && t.includes("message") || t.length >= 3 && t.includes("message") && t.includes("exceptionMessage") && t.includes("exceptionStacktrace");
}
function P() {
	let e = "A server restart is required";
	return A() ? n(p`${e}${F()}`) : n(p`${e}`);
}
function F() {
	return A() ? p`<vaadin-button
      class="mt-2"
      theme="primary"
      @click=${(e) => {
		let t = e.target;
		t.disabled = !0, t.innerText = "Restarting...", j();
	}}>
      Restart Now
    </vaadin-button>` : d;
}
function I(e, r) {
	let i = r;
	if (i && Y(i)) {
		X(i);
		return;
	}
	let a = N(r) ? r.exceptionMessage ?? r.message : r, o = {
		type: s.ERROR,
		message: "Copilot internal error",
		details: e + (a ? `\n${a}` : "")
	};
	N(r) && r.suggestRestart && A() && (o.details = n(p`${e}<br />${a} ${F()}`), o.delay = 3e4), t(o);
	let c;
	c = r instanceof Error ? r.stack : N(r) ? r?.exceptionStacktrace?.join("\n") : r?.toString(), g.emit("system-info-with-callback", {
		callback: (t) => g.send("copilot-error", {
			message: `Copilot internal error: ${e}`,
			details: c,
			versions: t
		}),
		notify: !1
	});
}
function L(e) {
	return e?.stack?.includes("cdn.vaadin.com/copilot") || e?.stack?.includes("/copilot/copilot/") || e?.stack?.includes("/copilot/copilot-private/");
}
function R() {
	let e = window.onerror;
	window.onerror = (t, n, r, i, a) => {
		if (L(a)) {
			I(t.toString(), a);
			return;
		}
		e && e(t, n, r, i, a);
	}, l((e) => {
		L(e) && I("", e);
	});
	let t = window.Vaadin.ConsoleErrors;
	if (Array.isArray(t)) for (let e of t) Array.isArray(e) ? Q.push(...e) : Q.push(e);
	B((e) => Q.push(e));
}
function z(e, t, n, r, i, a) {
	let o = { ...e }, s = window.Vaadin.copilot.tree, c = window.Vaadin.copilot.customComponentHandler;
	o.nodes.forEach((e) => {
		e.node = s.allNodesFlat.find((t) => {
			if (!t.isFlowComponent) return !1;
			let n = t.node;
			return n.uiId === e.uiId && n.nodeId === e.nodeId;
		});
	});
	let l = [];
	n && l.push(`Error Message -> ${n}`), r && l.push(`Error Details -> ${r}`), l.push(`Active Level -> ${c.getActiveDrillDownContext() ? c.getActiveDrillDownContext()?.nameAndIdentifier : "No active level"}`), o.nodes.length > 0 && (l.push("\nRelevant Nodes:"), o.nodes.forEach((e) => {
		l.push(`${e.relevance} -> ${e.node?.nameAndIdentifier ?? "Node not found"}`);
	})), o.relevantPairs.length > 0 && (l.push("\nAdditional Info:"), o.relevantPairs.forEach((e) => {
		l.push(`${e.relevance} -> ${e.value}`);
	})), a && (l.push("Versions"), l.push(a));
	let u = {
		name: "Info",
		content: l.join("\n")
	};
	o.items.unshift(u), i && o.items.push({
		name: "Stacktrace",
		content: i
	}), g.emit("system-info-with-callback", {
		callback: (e) => {
			o.items.push({
				name: "Versions",
				content: e
			}), t(o);
		},
		notify: !1
	});
}
function B(e) {
	let t = window.Vaadin.ConsoleErrors;
	window.Vaadin.ConsoleErrors = { push: (n) => {
		n[0] === null || n[0] === void 0 || (n[0].type !== void 0 && n[0].message !== void 0 ? e({
			type: n[0].type,
			message: n[0].message,
			internal: !!n[0].internal,
			details: n[0].details,
			link: n[0].link
		}) : e({
			type: s.ERROR,
			message: n.map((e) => V(e)).join(" "),
			internal: !1
		}), t.push(n));
	} };
}
function V(e) {
	return e.message ? e.message.toString() : e.toString();
}
var H, U, W, G, K, q, J, Y, X, Z, Q, $ = e((() => {
	f(), D(), m(), h(), v(), k(), M(), r(), o(), u(), H = (e, t) => e.error ? (Z(e.error, t), !0) : !1, U = (e, r, i) => {
		t({
			type: s.ERROR,
			message: e,
			details: n(p`${W(r)} ${K(i)}`),
			delay: 3e4
		});
	}, W = (e) => e.length === 0 ? d : e.length < 80 ? G(e) : p`<vaadin-details class="flex flex-col peer w-full" theme="no-padding reverse">
    <vaadin-details-summary class="font-medium -ms-3 self-start text-secondary text-xs" slot="summary"
      >Details</vaadin-details-summary
    >
    ${G(e)}
  </vaadin-details>`, G = (e) => p`<code
    class="bg-gray-3 dark:bg-gray-6 box-border inline-block mb-3 mt-2 pe-8 ps-3 py-1.75 relative rounded-md text-xs truncate w-full"
    >${T(e)}<copilot-copy></copilot-copy
  ></code>`, K = (e) => e ? p`
    <vaadin-button
      class="peer-has-[[opened]]:mt-2"
      @click="${() => {
		e && g.emit("submit-exception-report-clicked", e);
	}}"
      id="report-issue">
      <vaadin-icon slot="prefix" .svg="${te.bugReport}"></vaadin-icon>
      Report Issue</vaadin-button
    >
  ` : d, q = (e, t, n, r, i) => {
		i ? z(i, (n) => {
			U(e, t, n);
		}, e, t, n) : U(e, t), O() && (r?.templateData && typeof r.templateData == "string" && r.templateData.startsWith("data") && (r.templateData = "<IMAGE_DATA>"), g.emit("system-info-with-callback", {
			callback: (t) => g.send("copilot-error", {
				message: e,
				details: String(n).replace("	", "\n") + (r ? `\n \nRequest: \n${JSON.stringify(r)}\n` : ""),
				versions: t
			}),
			notify: !1
		})), _.clearOperationWaitsHmrUpdate();
	}, J = ["unsupported-source-language", "mixed-language-not-supported"], Y = (e) => !!e?.code && J.includes(e.code), X = (e) => {
		t({
			type: s.WARNING,
			message: e.exceptionMessage?.trim() || e.message
		}), _.clearOperationWaitsHmrUpdate();
	}, Z = (e, t) => {
		if (Y(e)) {
			X(e);
			return;
		}
		q(e.message, e.exceptionMessage ?? "", e.exceptionStacktrace?.join("\n") ?? "", t, e.exceptionReport);
	}, Q = [];
}));
//#endregion
export { H as a, M as c, le as d, D as f, I as i, j as l, Q as n, $ as o, T as p, P as r, R as s, B as t, A as u };
