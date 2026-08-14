import { injectGlobalWebcomponentCss } from 'Frontend/generated/jar-resources/theme-util.js';

import '@vaadin/vertical-layout/src/vaadin-vertical-layout.js';
import '@vaadin/tabs/src/vaadin-tabs.js';
import '@vaadin/tabs/src/vaadin-tab.js';
import '@vaadin/tooltip/src/vaadin-tooltip.js';
import '@vaadin/select/src/vaadin-select.js';
import 'Frontend/generated/jar-resources/selectConnector.js';
import 'Frontend/generated/jar-resources/flow-component-renderer.js';
import 'Frontend/generated/jar-resources/flow-component-directive.js';
import 'lit';
import 'Frontend/generated/jar-resources/lit-renderer.ts';
import 'lit/directives/live.js';
import '@vaadin/icons/vaadin-iconset.js';
import '@vaadin/icon/src/vaadin-icon.js';
import '@vaadin/text-field/src/vaadin-text-field.js';
import '@vaadin/button/src/vaadin-button.js';
import 'Frontend/generated/jar-resources/disableOnClickFunctions.js';
import '@vaadin/notification/src/vaadin-notification.js';
import '@vaadin/dialog/src/vaadin-dialog.js';
import 'Frontend/confetti.js';
import '@vaadin/checkbox/src/vaadin-checkbox.js';
import '@vaadin/horizontal-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/integer-field/src/vaadin-integer-field.js';
import '@vaadin/upload/src/vaadin-upload.js';
import 'Frontend/generated/jar-resources/vaadin-upload-manager-connector.ts';
import '@vaadin/upload/vaadin-upload-manager.js';
import '@vaadin/common-frontend/ConnectionIndicator.js';
import 'Frontend/generated/jar-resources/ReactRouterOutletElement.tsx';
import 'react-router';
import 'react';

const loadOnDemand = (key) => {
  const pending = [];
  if (key === '375552b76618f51390aa985cfa7a0311d9cc23d2907cc8d5836f42c415ac2113') {
    pending.push(import('./chunks/chunk-2d841c7abca686246cdf3fc71e866169ee4f9ad76a4c4eb2bbf0669424a4f712.js'));
  }
  if (key === '27547d7f28bcd4c82a4641797d1d8bdb4e1629374a736e6284343300afa8a8a7') {
    pending.push(import('./chunks/chunk-2d841c7abca686246cdf3fc71e866169ee4f9ad76a4c4eb2bbf0669424a4f712.js'));
  }
  return Promise.all(pending);
}
window.Vaadin = window.Vaadin || {};
window.Vaadin.Flow = window.Vaadin.Flow || {};
window.Vaadin.Flow.loadOnDemand = loadOnDemand;
window.Vaadin.Flow.resetFocus = () => {
 let ae=document.activeElement;
 while(ae&&ae.shadowRoot) ae = ae.shadowRoot.activeElement;
 return !ae || ae.blur() || ae.focus() || true;
}