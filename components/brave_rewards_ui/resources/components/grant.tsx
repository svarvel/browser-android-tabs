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
} from 'brave-ui/src/features/rewards'

// Utils
import * as rewardsActions from '../actions/rewards_actions'
import { convertProbiToFixed } from '../utils'

type Step = '' | 'complete'

interface State {
  grantShown: boolean
  grantStep: Step
  loading: boolean
}

interface Props extends Rewards.ComponentProps {
}

// TODO add local when we know what we will get from the server
class Grant extends React.Component<Props, State> {
  constructor (props: Props) {
    super(props)
    this.state = {
      grantShown: true,
      grantStep: '',
      loading: false
    }
  }

  get actions () {
    return this.props.actions
  }

  onClaim = () => {
    this.setState({ loading: true })
    this.actions.getGrantCaptcha()
    this.setState({ grantStep: 'complete' })
  }

  onSuccess = () => {
    this.setState({
      grantShown: false,
      loading: false
    })
    this.actions.onDeleteGrant()
  }

  validateGrant = (tokens?: string) => {
    const { grant, safetyNetFailed } = this.props.rewardsData

    if (!tokens || !grant) {
      return false
    }

    if (safetyNetFailed) {
      this.setState({ loading: false })
      return false
    }

    return (tokens !== '0.0' && grant.expiryTime)
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

    // Guard against null grant statuses
    const validGrant = this.validateGrant(tokens)

    return (
      <React.Fragment>
        {
          this.state.grantShown
          ? <GrantClaim
            type={'ugp'}
            isMobile={true}
            onClaim={this.onClaim}
            loading={this.state.loading}
          />
          : null
        }
        {
          this.state.grantStep === 'complete' && validGrant
            ? <GrantWrapper
              fullScreen={true}
              onClose={this.onSuccess}
              title={'It’s your lucky day!'}
              text={'Your token grant is on its way.'}
            >
              <GrantComplete
                isMobile={true}
                onClose={this.onSuccess}
                amount={tokens}
                date={new Date(grant.expiryTime).toLocaleDateString()}
              />
            </GrantWrapper>
            : null
        }
      </React.Fragment>
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
