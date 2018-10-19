/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import * as React from 'react'
import { bindActionCreators, Dispatch } from 'redux'
import { connect } from 'react-redux'

// Components
import {
  GrantClaim,
  GrantComplete,
  GrantWrapper
} from 'brave-ui/features/rewards'

// Utils
import * as rewardsActions from '../actions/rewards_actions'
import { convertProbiToFixed } from '../utils'

type Step = '' | 'complete'

interface State {
  grantShown: boolean
  grantStep: Step
}

interface Props extends Rewards.ComponentProps {
}

// TODO add local when we know what we will get from the server
class Grant extends React.Component<Props, State> {
  constructor (props: Props) {
    super(props)
    this.state = {
      grantShown: true,
      grantStep: ''
    }
  }

  get actions () {
    return this.props.actions
  }

  onClaim = () => {
    this.setState({ grantStep: 'complete' })
  }

  onGrantHide = () => {
    this.actions.onResetGrant()
    this.setState({ grantStep: '' })
  }

  onSuccess = () => {
    this.setState({
      grantShown: false
    })
    this.actions.onDeleteGrant()
  }

  render () {
    const { grant } = this.props.rewardsData

    if (!grant) {
      return null
    }

    let tokens = '0.0'
    if (grant.probi) {
      tokens = convertProbiToFixed(grant.probi)
    }

    return (
      <>
        {
          this.state.grantShown
          ? <GrantClaim
            isMobile={true}
            onClaim={this.onClaim}
          />
          : null
        }
        {
          this.state.grantStep === 'complete'
            ? <GrantWrapper
              fullScreen={true}
              onClose={this.onGrantHide}
              title={'It’s your lucky day!'}
              text={'Your token grant is on its way.'}
            >
              <GrantComplete onClose={this.onSuccess} amount={tokens} date={new Date(grant.expiryTime).toLocaleDateString()} />
            </GrantWrapper>
            : null
        }
      </>
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
)(Grant)
