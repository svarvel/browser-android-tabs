/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import { action } from 'typesafe-actions'

// Constants
import { types } from '../constants/rewards_panel_types'

export const createWallet = () => action(types.CREATE_WALLET, {})

export const onWalletCreated = () => action(types.ON_WALLET_CREATED, {})

export const onWalletCreateFailed = () => action(types.ON_WALLET_CREATE_FAILED, {})

export const onBalanceReports = (reports: Record<string, Rewards.Report>) => action(types.ON_CURRENT_REPORT, {
  reports
})

export const onTabId = (tabId: number | undefined) => action(types.ON_TAB_ID, {
  tabId
})

export const onTabRetrieved = (tab: chrome.tabs.Tab) => action(types.ON_TAB_RETRIEVED, {
  tab
})

export const getPublisherData = (tabId: string, url: string) => action(types.GET_PUBLISHER_DATA, {
  tabId,
  url
})

export const onPublisherData = (tabId: string, publisher: RewardsExtension.Publisher) => action(types.ON_PUBLISHER_DATA, {
  tabId,
  publisher
})

export const getWalletProperties = () => action(types.GET_WALLET_PROPERTIES, {})

export const onWalletProperties = (properties: RewardsExtension.WalletProperties) => action(types.ON_WALLET_PROPERTIES, {
  properties
})

export const getCurrentReport = () => action(types.GET_CURRENT_REPORT, {})

export const onCurrentReport = (properties: RewardsExtension.Report) => action(types.ON_CURRENT_REPORT, {
  properties
})

export const currentTabInfo = () => action(types.GET_CURRENT_TAB_INFO, {})

export const onCurrentTabInfo = (currentTabInfo: {id: string, url: string}) => action(types.ON_CURRENT_TAB_INFO, {
  currentTabInfo
})
