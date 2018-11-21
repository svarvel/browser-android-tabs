/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import { types } from '../constants/donate_types'
import * as storage from '../storage'

export const rewardsDonateReducer = (state: RewardsDonate.State | undefined, action: any) => {
  if (state === undefined) {
    state = storage.load()
  }

  const startingState = state
  const payload = action.payload

  switch (action.type) {
    case types.ON_CLOSE_DIALOG:
      chrome.send('dialogClose')
      break
    case types.ON_PUBLISHER_BANNER:
      {
        state = { ...state }
        state.publisher = payload.data
        break
      }
    case types.GET_WALLET_PROPERTIES:
      chrome.send('brave_rewards_donate.getWalletProperties')
      break
    case types.ON_WALLET_PROPERTIES:
      {
        state = { ...state }
        if (payload.properties.status !== 1) {
          state.walletInfo = payload.properties.wallet
        }
        break
      }
    case types.ON_DONATE:
      {
        if (state.publisher && state.publisher.publisherKey && payload.amount > 0) {
          chrome.send('brave_rewards_donate.onDonate', [
            payload.publisherKey,
            parseInt(payload.amount, 10),
            payload.recurring
          ])
          state = { ...state }
          state.finished = true
        } else {
          // TODO return error
        }
        break
      }
    case types.GET_CURRENT_TAB_INFO:
      {
        state = { ...state }
        chrome.send('brave_rewards_donate.getCurrentActiveTabInfo', [])
        break
      }
    case types.ON_CURRENT_TAB_INFO:
      {
        state = { ...state }
        chrome.send('brave_rewards_donate.getPublisherData', [payload.currentTabInfo.id, payload.currentTabInfo.url])
        break
      }
    case types.ON_PUBLISHER_DATA:
      {
        let publisherKey = payload.info.publisher.publisher_key

        if (!publisherKey) {
          break
        }

        chrome.send('brave_rewards_donate.getPublisherBanner', [publisherKey])
        break
      }
    case types.GET_RECURRING_DONATIONS:
      chrome.send('brave_rewards_donate.getRecurringDonations')
      break
    case types.ON_RECURRING_DONATIONS:
      state = { ...state }
      state.recurringList = payload.list
      break
  }

  if (state !== startingState) {
    storage.debouncedSave(state)
  }

  return state
}
