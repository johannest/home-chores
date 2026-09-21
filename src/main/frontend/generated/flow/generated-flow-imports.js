import '@vaadin/vertical-layout/src/vaadin-vertical-layout.js';
import '@vaadin/tabs/src/vaadin-tabs.js';
import '@vaadin/tabs/src/vaadin-tab.js';
import '@vaadin/tooltip/src/vaadin-tooltip.js';
import 'Frontend/generated/jar-resources/vaadin-menu-bar/menubarConnector.ts';
import 'Frontend/generated/jar-resources/vaadin-context-menu/contextMenuConnector.ts';
import '@vaadin/menu-bar/src/vaadin-menu-bar.js';
import '@vaadin/context-menu/src/vaadin-context-menu.js';
import 'Frontend/generated/jar-resources/flow-component-renderer.js';
import 'Frontend/generated/jar-resources/flow-component-directive.js';
import 'lit';
import 'Frontend/generated/jar-resources/vaadin-context-menu/contextMenuTargetConnector.ts';
import '@vaadin/component-base/src/gestures.js';
import 'Frontend/generated/jar-resources/disableOnClickFunctions.js';
import '@vaadin/icons/vaadin-iconset.js';
import '@vaadin/icon/src/vaadin-icon.js';
import '@vaadin/select/src/vaadin-select.js';
import 'Frontend/generated/jar-resources/lit-renderer.ts';
import 'lit/directives/live.js';
import '@vaadin/progress-bar/src/vaadin-progress-bar.js';
import '@vaadin/text-field/src/vaadin-text-field.js';
import '@vaadin/checkbox/src/vaadin-checkbox.js';
import '@vaadin/button/src/vaadin-button.js';
import '@vaadin/dialog/src/vaadin-dialog.js';
import '@vaadin/notification/src/vaadin-notification.js';
import 'Frontend/confetti.js';
import '@vaadin/details/src/vaadin-details.js';
import '@vaadin/text-area/src/vaadin-text-area.js';
import '@vaadin/integer-field/src/vaadin-integer-field.js';
import '@vaadin/date-time-picker/src/vaadin-date-time-picker.js';
import '@vaadin/date-picker/src/vaadin-date-picker.js';
import 'Frontend/generated/jar-resources/vaadin-date-picker/datepickerConnector.ts';
import 'date-fns/parse';
import '@vaadin/date-picker/src/vaadin-date-picker-helper.js';
import '@vaadin/date-picker/src/vaadin-date-picker-mixin.js';
import '@vaadin/time-picker/src/vaadin-time-picker.js';
import 'Frontend/generated/jar-resources/vaadin-time-picker/timepickerConnector.ts';
import 'Frontend/generated/jar-resources/vaadin-time-picker/helpers.ts';
import '@vaadin/horizontal-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/checkbox-group/src/vaadin-checkbox-group.js';
import '@vaadin/upload/src/vaadin-upload.js';
import 'Frontend/generated/jar-resources/vaadin-upload/uploadManagerConnector.ts';
import '@vaadin/upload/vaadin-upload-manager.js';
import '@vaadin/common-frontend/ConnectionIndicator.js';
import 'Frontend/generated/jar-resources/ReactRouterOutletElement.tsx';
import 'react-router';
import 'react';

const loadOnDemand = (key) => {
  const pending = [];
  if (key === '95e10ba2625104a148eb9b32fd196fec3a4ebc3d9ecec9dca85ad66729d0e28f') {
    pending.push(import('./chunks/chunk-52318af722ea309cb0a82c0c6a072c8de93835cf8fbfe25b8c9db112b2e56ca3.js'));
  }
  if (key === '27547d7f28bcd4c82a4641797d1d8bdb4e1629374a736e6284343300afa8a8a7') {
    pending.push(import('./chunks/chunk-f6eba299fc8d8d80c560066d68fa0addaeb6b852699fd7a60130c5546ad8adf7.js'));
  }
  if (key === '375552b76618f51390aa985cfa7a0311d9cc23d2907cc8d5836f42c415ac2113') {
    pending.push(import('./chunks/chunk-52318af722ea309cb0a82c0c6a072c8de93835cf8fbfe25b8c9db112b2e56ca3.js'));
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