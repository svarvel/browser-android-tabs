/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import * as React from 'react'
import { render } from 'react-dom'
import { Provider } from 'react-redux'
import store from './store'
import { bindActionCreators } from 'redux'

// Components
import App from './components/app'
require('emptykit.css')

// Utils
import Theme from 'brave-ui/src/theme/brave-default'
import { ThemeProvider } from 'brave-ui/src/theme'
import { initLocale } from 'brave-ui/src/helpers'
import * as rewardsActions from './actions/donate_actions'

let actions: any

window.cr.define('brave_rewards_donate', function () {
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
      document.getElementById('root')
    )

    // Get active tab info
    getActions().currentTabInfo()
  }

  function getActions () {
    if (actions) {
      return actions
    }

    actions = bindActionCreators(rewardsActions, store.dispatch.bind(store))
    return actions
  }

  function publisherBanner (data: RewardsDonate.Publisher) {
    getActions().onPublisherBanner(data)
  }

  function walletProperties (properties: {status: number, wallet: RewardsDonate.WalletProperties}) {
    getActions().onWalletProperties(properties)
  }

  function recurringDonations (list: string[]) {
    getActions().onRecurringDonations(list)
  }

  function currentTabInfo (currentTabInfo: {id: number, url: string}) {
    getActions().onCurrentTabInfo(currentTabInfo)
  }

  function publisherData (info: RewardsDonate.PublisherPayload) {
    getActions().onPublisherData(info)
  }

  return {
    initialize,
    publisherBanner,
    walletProperties,
    recurringDonations,
    currentTabInfo,
    publisherData
  }
})

document.addEventListener('DOMContentLoaded', window.brave_rewards_donate.initialize)
