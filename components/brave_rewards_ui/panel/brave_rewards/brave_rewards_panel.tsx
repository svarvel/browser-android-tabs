/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import * as React from 'react'
import { render } from 'react-dom'
import { Provider } from 'react-redux'
import store from './background/store'
import { bindActionCreators } from 'redux'

import Theme from 'brave-ui/theme/brave-default'
import { ThemeProvider } from 'brave-ui/theme'
import { initLocale } from 'brave-ui/helpers'
import { getActions as getUtilActions, setActions } from '../../resources/utils'
import * as rewardsPanelActions from './actions/rewards_panel_actions'

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
      document.getElementById('root')
    )
  }

  function getActions () {
    const actions: any = getUtilActions()
    if (actions) {
      return actions
    }
    const newActions = bindActionCreators(rewardsPanelActions, store.dispatch.bind(store))
    setActions(newActions)
    return newActions
  }

  function currentTabInfo (currentTabInfo: {id: number, url: string}) {
    getActions().onCurrentTabInfo(currentTabInfo)
  }

  function publisherData (info: RewardsExtension.PublisherPayload) {
    getActions().onPublisherData(info)
  }

  function walletCreated () {
    getActions().onWalletCreated()
  }

  function walletCreateFailed () {
    getActions().onWalletCreateFailed()
  }

  function balanceReports (reports: Record<string, Rewards.Report>) {
    getActions().onBalanceReports(reports)
  }

  function walletProperties (properties: {status: number, wallet: Rewards.WalletProperties}) {
    getActions().onWalletProperties(properties)
  }

  function walletExists (exists: boolean) {
    getActions().onWalletExists(exists)
  }

  return {
    initialize,
    balanceReports,
    walletCreated,
    walletCreateFailed,
    walletProperties,
    currentTabInfo,
    publisherData,
    walletExists
  }
})

document.addEventListener('DOMContentLoaded', window.brave_rewards_panel.initialize)
