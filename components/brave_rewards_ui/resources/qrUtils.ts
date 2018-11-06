/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * you can obtain one at http://mozilla.org/MPL/2.0/. */

import { actions } from './utils'
// No point using dynamic import for this reference as this whole
// module should be dynamic imported due to it's Buffer reference.
import * as qr from 'qr-image'

export const generateQR = async function (addresses: Record<Rewards.AddressesType, string>) {
  let url = null
  const generate = (type: Rewards.AddressesType, address: string) => {
    switch (type) {
      case 'BAT':
      case 'ETH':
        url = `ethereum:${address}`
        break
      case 'BTC':
        url = `bitcoin:${address}`
        break
      case 'LTC':
        url = `litecoin:${address}`
        break
      default:
        return
    }

    try {
      let chunks: Uint8Array[] = []

      qr.image(url, { type: 'png' })
        .on('data', (chunk: Uint8Array) => {
          chunks.push(chunk)
        })
        .on('end', () => {
          const qrImage = 'data:image/png;base64,' + Buffer.concat(chunks).toString('base64')
          if (actions) {
            actions.onQRGenerated(type, qrImage)
          }
        })
    } catch (ex) {
      console.error('qr.imageSync (for url ' + url + ') error: ' + ex.toString())
    }
  }

  for (let type in addresses) {
    generate(type as Rewards.AddressesType, addresses[type])
  }
}