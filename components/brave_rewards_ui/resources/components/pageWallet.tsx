/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import * as React from 'react'
import { bindActionCreators, Dispatch } from 'redux'
import { connect } from 'react-redux'

// Components
import { AlertWallet } from 'brave-ui/features/rewards/walletWrapper'
import { ModalAddFunds, WalletSummary, WalletWrapper } from 'brave-ui/features/rewards'
import { CloseStrokeIcon, WalletAddIcon } from 'brave-ui/components/icons'
import { StyledWalletClose, StyledWalletOverlay, StyledWalletWrapper } from './style'

// Utils
import { getLocale } from '../../../common/locale'
import * as rewardsActions from '../actions/rewards_actions'
import * as utils from '../utils'
import { convertProbiToFixed } from '../../../common/probiUtils'

interface State {
  addFundsShown?: boolean
}

interface Props extends Rewards.ComponentProps {
  visible?: boolean
  toggleAction: () => void
}

class PageWallet extends React.Component<Props, State> {
  constructor (props: Props) {
    super(props)

    this.state = {
      addFundsShown: false
    }
  }

  get actions () {
    return this.props.actions
  }

  notImplemented = () => {
    // Feature not implemented
  }

  toggleAddFunds = () => {
    this.setState({ addFundsShown: !this.state.addFundsShown })
  }

  getConversion = () => {
    const walletInfo = this.props.rewardsData.walletInfo
    return utils.convertBalance(walletInfo.balance.toString(), walletInfo.rates)
  }

  getGrants = () => {
    const grants = this.props.rewardsData.walletInfo.grants
    if (!grants) {
      return []
    }

    return grants.map((grant: Rewards.Grant) => {
      return {
        tokens: convertProbiToFixed(grant.probi),
        expireDate: new Date(grant.expiryTime * 1000).toLocaleDateString()
      }
    })
  }

  walletAlerts = (): AlertWallet | null => {
    const { walletServerProblem } = this.props.rewardsData.ui

    if (walletServerProblem) {
      return {
        node: <React.Fragment><b>{getLocale('uhOh')}</b> {getLocale('serverNotResponding')}</React.Fragment>,
        type: 'error'
      }
    }

    return null
  }

  getWalletSummary = () => {
    const { walletInfo, reports } = this.props.rewardsData
    const { rates } = walletInfo

    let props = {}

    const currentTime = new Date()
    const reportKey = `${currentTime.getFullYear()}_${currentTime.getMonth() + 1}`
    const report: Rewards.Report = reports[reportKey]
    if (report) {
      for (let key in report) {
        const item = report[key]

        if (item.length > 1 && key !== 'total') {
          const tokens = convertProbiToFixed(item)
          props[key] = {
            tokens,
            converted: utils.convertBalance(tokens, rates)
          }
        }
      }
    }

    return {
      report: props
    }
  }

  render () {
    const { visible, toggleAction } = this.props
    const { connectedWallet, addresses, walletInfo } = this.props.rewardsData
    const { balance } = walletInfo
    const addressArray = utils.getAddresses(addresses)

    if (!visible) {
      return null
    }

    return (
      <React.Fragment>
        <StyledWalletOverlay>
          <StyledWalletClose>
            <CloseStrokeIcon onClick={toggleAction}/>
          </StyledWalletClose>
          <StyledWalletWrapper>
            <WalletWrapper
              balance={balance.toFixed(1)}
              converted={utils.formatConverted(this.getConversion())}
              actions={[
                {
                  name: getLocale('panelAddFunds'),
                  action: this.toggleAddFunds,
                  icon: <WalletAddIcon />
                }
              ]}
              compact={true}
              isMobile={true}
              onSettingsClick={this.notImplemented}
              onActivityClick={this.notImplemented}
              showSecActions={true}
              grants={this.getGrants()}
              alert={this.walletAlerts()}
              connectedWallet={connectedWallet}
            >
              <WalletSummary {...this.getWalletSummary()}/>
            </WalletWrapper>
          </StyledWalletWrapper>
          {
            this.state.addFundsShown
            ? <ModalAddFunds isMobile={true} onClose={this.toggleAddFunds} addresses={addressArray} />
            : null
          }
        </StyledWalletOverlay>
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
)(PageWallet)
