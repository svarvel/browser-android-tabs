/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import { types } from '../../constants/rewards_panel_types'
import * as storage from '../storage'
import { getTabData } from '../api/tabs_api'

export const rewardsPanelReducer = (state: RewardsExtension.State | undefined, action: any) => {
  if (state === undefined) {
    state = storage.load()
  }

  const startingState = state

  const payload = action.payload
  switch (action.type) {
    case types.CREATE_WALLET:
      chrome.send('brave_rewards_panel.createWalletRequested', [])
      break
    case types.ON_WALLET_CREATED:
      state = { ...state }
      state.walletCreated = true
      break
    case types.ON_WALLET_CREATE_FAILED:
      state = { ...state }
      state.walletCreateFailed = true
      break
    case types.ON_TAB_ID:
      if (payload.tabId) {
        getTabData(payload.tabId)
      }
      break
    case types.GET_PUBLISHER_DATA:
      chrome.send('brave_rewards_panel.getPublisherData', [action.payload.tabId, action.payload.url])
      break
    case types.ON_PUBLISHER_DATA:
      {
        // ToDo: Primary index should not be tabId
        let publisher = payload.tabId
        let tabId = payload.tabId.tabId
        let publishers: Record<string, RewardsExtension.Publisher> = state.publishers

        if (publisher && !publisher.publisher_key) {
          delete publishers[tabId]
        } else {
          publishers[tabId] = publisher
        }

        state = {
          ...state,
          publishers
        }
        break
      }
    case types.GET_WALLET_PROPERTIES:
      chrome.send('brave_rewards_panel.getWalletProperties', [])
      break
    case types.ON_WALLET_PROPERTIES:
      {
        state = { ...state }
        state.walletProperties = payload.properties
        break
      }
    case types.GET_CURRENT_REPORT:
      chrome.send('brave_rewards_panel.getCurrentReport', [])
      break
    case types.ON_CURRENT_REPORT:
      {
        state = { ...state }
        state.report = payload.properties
        break
      }
    case types.GET_CURRENT_TAB_INFO:
      chrome.send('brave_rewards_panel.getCurrentActiveTabInfo', [])
      break
    case types.ON_CURRENT_TAB_INFO:
      {
        state = { ...state }
        state.currentTabId = payload.currentTabInfo.id
        state.currentTabUrl = payload.currentTabInfo.url
        break
      }
  }

  if (state !== startingState) {
    storage.debouncedSave(state)
  }

  return state
}
