/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import * as React from 'react'
import { bindActionCreators, Dispatch } from 'redux'
import { connect } from 'react-redux'

// Components
import { BoxMobile } from 'brave-ui/features/rewards/mobile'

// Utils
import { getLocale } from '../../../common/locale'
import * as rewardsActions from '../actions/rewards_actions'

class AdsBox extends React.Component {
  render () {
    return (
      <BoxMobile
        title={getLocale('adsTitle')}
        type={'ads'}
        description={getLocale('adsDesc')}
        toggle={false}
        checked={false}
      />
    )
  }
}

const mapStateToProps = (state: Rewards.ApplicationState) => ({
  rewardsData: state.rewardsData
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
  actions: bindActionCreators(rewardsActions, dispatch)
})

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(AdsBox)
