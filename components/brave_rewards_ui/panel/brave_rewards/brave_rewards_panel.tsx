/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import * as React from 'react'
import { render } from 'react-dom'
import { Provider } from 'react-redux'
import store from './background/store'

import Theme from 'brave-ui/theme/brave-default'
import { ThemeProvider } from 'brave-ui/theme'
import { initLocale } from 'brave-ui/helpers'
require('emptykit.css')
require('../../../fonts/muli.css')
require('../../../fonts/poppins.css')

// Components
import App from './components/app'

window.cr.define('brave_rewards_panel', function () {
  'use strict'

  function initialize () {
    window.i18nTemplate.process(window.document, window.loadTimeData)
    if (window.loadTimeData && window.loadTimeData.data_) {
      initLocale(window.loadTimeData.data_)
    }

	render(
	  <Provider store={store}>
	    <ThemeProvider theme={Theme}>
	      <App />
	    </ThemeProvider>
	  </Provider>,
	  document.getElementById('root'))
  }

  return {
    initialize
  }
})

document.addEventListener('DOMContentLoaded', window.brave_rewards_panel.initialize)