/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import * as React from 'react'
import { bindActionCreators, Dispatch } from 'redux'
import { connect } from 'react-redux'

// Components
import { AlertWallet } from 'brave-ui/src/features/rewards/walletWrapper'
import {
  ModalAddFunds,
  WalletSummary,
  WalletWrapper,
  WalletEmpty,
  WalletOff
} from 'brave-ui/src/features/rewards'
import { CloseStrokeIcon, WalletAddIcon } from 'brave-ui/src/components/icons'
import { StyledWalletClose, StyledWalletOverlay, StyledWalletWrapper } from './style'

// Utils
import { getLocale } from '../../../common/locale'
import * as rewardsActions from '../actions/rewards_actions'
import * as utils from '../utils'
import { convertProbiToFixed } from '../../../common/probiUtils'

interface State {
  activeTabId: number
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
      activeTabId: 0,
      addFundsShown: false
    }
  }

  get actions () {
    return this.props.actions
  }

  componentDidMount () {
    this.isAddFundsUrl()
  }

  isAddFundsUrl = () => {
    if (window &&
        window.location &&
        window.location.hash &&
        window.location.hash === '#add-funds') {
      this.setState({
        addFundsShown: true
      })
    } else {
      this.setState({
        addFundsShown: false
      })
    }
  }

  notImplemented = () => {
    // Feature not implemented
  }

  toggleAddFunds = () => {
    if (this.state.addFundsShown) {
      if (window &&
          window.location &&
          window.location.href) {
        window.location.href = 'chrome://rewards'
      }
    }
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
          const tokens = utils.convertProbiToFixed(item)
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
    const {
      enabledMain,
      connectedWallet,
      addresses,
      walletInfo,
      ui,
      pendingContributionTotal
    } = this.props.rewardsData
    const { emptyWallet } = ui
    const { balance } = walletInfo
    const addressArray = utils.getAddresses(addresses)
    const pendingTotal = parseFloat((pendingContributionTotal || 0).toFixed(1))

    if (!visible && !this.state.addFundsShown) {
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
              onActivityClick={this.notImplemented}
              showSecActions={false}
              grants={this.getGrants()}
              alert={this.walletAlerts()}
              connectedWallet={connectedWallet}
            >
              {
                enabledMain
                ? emptyWallet
                  ? <WalletEmpty />
                  : <WalletSummary
                    reservedAmount={pendingTotal}
                    reservedMoreLink={'https://brave.com/faq-rewards/#unclaimed-funds'}
                    {...this.getWalletSummary()}
                  />
                : <WalletOff/>
              }
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
