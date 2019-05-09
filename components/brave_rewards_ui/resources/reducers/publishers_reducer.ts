/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import { Reducer } from 'redux'

// Constant
import { types } from '../constants/rewards_types'

const publishersReducer: Reducer<Rewards.State | undefined> = (state: Rewards.State, action) => {
  switch (action.type) {
    case types.ON_CONTRIBUTE_LIST:
      state = { ...state }
      state.firstLoad = false
      state.autoContributeList = action.payload.list
      break
    case types.ON_NUM_EXCLUDED_SITES:
      state = { ...state }

      if (action.payload.excludedSitesInfo != null) {
        const previousNum = state.numExcludedSites
        const newNum = action.payload.excludedSitesInfo.num
        const publisherKey = action.payload.excludedSitesInfo.publisherKey

        state.numExcludedSites = parseInt(newNum, 10)

        if (publisherKey.length > 0) {
          if (previousNum < newNum) {
            // On a new excluded publisher, add to excluded state
            if (!state.excluded.includes(publisherKey)) {
              state.excluded.push(publisherKey)
            }
          } else {
            // Remove the publisher from excluded if it has been re-included
            if (state.excluded.includes(publisherKey)) {
              state.excluded = state.excluded.filter((key: string) => key !== publisherKey)
            }
          }
        }

        state = {
          ...state,
          excluded: state.excluded
        }
      }

      break
    case types.ON_EXCLUDE_PUBLISHER:
      if (!action.payload.publisherKey) {
        break
      }
      chrome.send('brave_rewards.excludePublisher', [action.payload.publisherKey])
      break
    case types.ON_RESTORE_PUBLISHERS:
      chrome.send('brave_rewards.restorePublishers', [])
      break
    case types.GET_CONTRIBUTE_LIST:
      chrome.send('brave_rewards.getContributionList')
      break
    case types.ON_CURRENT_TIPS:
      state = { ...state }
      if (state.tipsLoad) {
        state.firstLoad = false
      } else {
        state.tipsLoad = true
      }
      state.tipsList = action.payload.list
      break
    case types.ON_RECURRING_DONATION_UPDATE:
      state = { ...state }
      if (state.recurringLoad) {
        state.firstLoad = false
      } else {
        state.recurringLoad = true
      }
      state.recurringList = action.payload.list
      break
    case types.ON_REMOVE_RECURRING:
      if (!action.payload.publisherKey) {
        break
      }
      chrome.send('brave_rewards.removeRecurringTip', [action.payload.publisherKey])
      break
    case types.ON_RECURRING_TIP_REMOVED:
      chrome.send('brave_rewards.getRecurringTips')
      break
  }

  return state
}

export default publishersReducer
