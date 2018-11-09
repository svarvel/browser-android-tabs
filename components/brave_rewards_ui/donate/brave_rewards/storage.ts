/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

// Utils
import { debounce } from '../../../common/debounce'

const keyName = 'rewards-donate-data'

export const defaultState: RewardsDonate.State = {
  finished: false,
  error: false,
  publisher: undefined,
  walletInfo: {
    balance: 0,
    choices: [],
    probi: '0'
  }
}

const cleanData = (state: RewardsDonate.State) => state

export const load = (): RewardsDonate.State => {
  const data = window.localStorage.getItem(keyName)
  let state: RewardsDonate.State = defaultState
  if (data) {
    try {
      state = JSON.parse(data)
    } catch (e) {
      console.error('Could not parse local storage data: ', e)
    }
  }
  return cleanData(state)
}

export const debouncedSave = debounce((data: RewardsDonate.State) => {
  if (data) {
    window.localStorage.setItem(keyName, JSON.stringify(cleanData(data)))
  }
}, 50)
